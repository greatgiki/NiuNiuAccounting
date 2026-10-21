package com.example.localledger.security

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.di.ApplicationScope
import com.example.localledger.ui.AppSession
import com.example.localledger.widget.LedgerWidgetRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class AppLockState(
    val phase: Phase = Phase.Unknown,
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = false,
    val timeoutMinutes: Int = 1,
    val unreadable: Boolean = false,
    val maskWidgetAmounts: Boolean = true,
) {
    enum class Phase { Unknown, Locked, Unlocked }

    val locked: Boolean get() = phase == Phase.Locked
}

@Singleton
class AppLockController @Inject constructor(
    private val store: AppLockStore,
    private val settingsStore: SettingsStore,
    private val appSession: AppSession,
    private val clock: Clock,
    private val widgetRefresher: LedgerWidgetRefresher,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val started = AtomicBoolean(false)
    private val unlockedThisProcess = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    @Volatile private var timeoutMinutes: Int = 1
    @Volatile private var backgroundedAt: Long = 0L
    private var skipNextForeground = true

    fun start() {
        if (!started.compareAndSet(false, true)) return
        skipNextForeground = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { hydrate() }
    }

    fun isLockConfigured(): Boolean = store.isConfigured()

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = clock.millis()
        if (store.isConfigured() && timeoutMinutes <= 0) {
            lockNow()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (skipNextForeground) {
            skipNextForeground = false
            return
        }
        val configured = store.isConfigured()
        if (shouldRelock(configured, timeoutMinutes, backgroundedAt, clock.millis())) {
            lockNow()
        }
        scope.launch { refreshTimeout() }
    }

    suspend fun unlock(pin: String): Boolean {
        if (mutableState.value.unreadable) return false
        val record = when (val read = store.read()) {
            is PinRead.Present -> read.record
            else -> return false
        }
        if (!record.matches(pin)) return false
        unlockedThisProcess.set(true)
        mutableState.update {
            it.copy(phase = AppLockState.Phase.Unlocked, hasPin = true, biometricEnabled = record.biometricEnabled, unreadable = false)
        }
        return true
    }

    fun unlockWithBiometric() {
        val current = mutableState.value
        if (!current.hasPin || !current.biometricEnabled || current.unreadable) return
        unlockedThisProcess.set(true)
        mutableState.update { it.copy(phase = AppLockState.Phase.Unlocked) }
    }

    suspend fun setup(pin: String, confirm: String): String? {
        PinPolicy.check(pin, confirm)?.let { return it }
        val record = newPinRecord(pin, biometricEnabled = false)
        store.write(record)
        unlockedThisProcess.set(true)
        mutableState.update {
            it.copy(phase = AppLockState.Phase.Unlocked, hasPin = true, biometricEnabled = false, unreadable = false)
        }
        widgetRefresher.request()
        return null
    }

    suspend fun change(oldPin: String, newPin: String, confirm: String): String? {
        val record = when (val read = store.read()) {
            is PinRead.Present -> read.record
            is PinRead.Unreadable -> return "无法读取应用锁，请先备份账本，再清除应用数据"
            PinRead.Absent -> return "尚未设置密码"
        }
        if (!record.matches(oldPin)) return "当前密码不正确"
        PinPolicy.check(newPin, confirm)?.let { return it }
        store.write(newPinRecord(newPin, record.biometricEnabled))
        mutableState.update { it.copy(hasPin = true, biometricEnabled = record.biometricEnabled) }
        return null
    }

    suspend fun disable(pin: String): String? {
        val record = when (val read = store.read()) {
            is PinRead.Present -> read.record
            is PinRead.Unreadable -> return "无法读取应用锁，请先备份账本，再清除应用数据"
            PinRead.Absent -> {
                clearState()
                widgetRefresher.request()
                return null
            }
        }
        if (!record.matches(pin)) return "密码不正确"
        store.clear()
        clearState()
        widgetRefresher.request()
        return null
    }

    suspend fun setBiometric(enabled: Boolean): String? {
        val record = when (val read = store.read()) {
            is PinRead.Present -> read.record
            is PinRead.Unreadable -> return "无法读取应用锁"
            PinRead.Absent -> return "请先设置数字密码"
        }
        val updated = record.copy(biometricEnabled = enabled)
        store.write(updated)
        mutableState.update { it.copy(biometricEnabled = enabled, hasPin = true) }
        return null
    }

    suspend fun setTimeout(minutes: Int) {
        val clamped = minutes.coerceIn(0, 15)
        timeoutMinutes = clamped
        appSession.updateSettings { it.copy(appLockTimeoutMinutes = clamped) }
        mutableState.update { it.copy(timeoutMinutes = clamped) }
    }

    suspend fun setMaskWidgetAmounts(enabled: Boolean) {
        appSession.updateSettings { it.copy(widgetMaskAmountsWhenLocked = enabled) }
        mutableState.update { it.copy(maskWidgetAmounts = enabled) }
        widgetRefresher.request()
    }

    private suspend fun hydrate() {
        try {
            refreshTimeout()
            when (val read = store.read()) {
                PinRead.Absent -> mutableState.update {
                    it.copy(phase = AppLockState.Phase.Unlocked, hasPin = false, biometricEnabled = false, unreadable = false)
                }
                is PinRead.Present -> {
                    val phase = if (unlockedThisProcess.get()) AppLockState.Phase.Unlocked else AppLockState.Phase.Locked
                    mutableState.update {
                        it.copy(
                            phase = phase,
                            hasPin = true,
                            biometricEnabled = read.record.biometricEnabled,
                            unreadable = false,
                            timeoutMinutes = timeoutMinutes,
                        )
                    }
                }
                PinRead.Unreadable -> mutableState.update {
                    it.copy(phase = AppLockState.Phase.Locked, hasPin = true, unreadable = true)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("LocalLedger", "读取应用锁失败", error)
            val configured = store.isConfigured()
            mutableState.update {
                it.copy(
                    phase = if (configured) AppLockState.Phase.Locked else AppLockState.Phase.Unlocked,
                    hasPin = configured,
                    unreadable = configured,
                )
            }
        }
    }

    private suspend fun refreshTimeout() {
        val settings = settingsStore.peek()
        val minutes = settings?.appLockTimeoutMinutes ?: timeoutMinutes
        timeoutMinutes = minutes.coerceIn(0, 15)
        val mask = settings?.widgetMaskAmountsWhenLocked ?: true
        mutableState.update { it.copy(timeoutMinutes = timeoutMinutes, maskWidgetAmounts = mask) }
    }

    private fun lockNow() {
        unlockedThisProcess.set(false)
        mutableState.update { it.copy(phase = AppLockState.Phase.Locked, hasPin = true) }
    }

    private fun clearState() {
        unlockedThisProcess.set(true)
        mutableState.update {
            it.copy(phase = AppLockState.Phase.Unlocked, hasPin = false, biometricEnabled = false, unreadable = false)
        }
    }
}
