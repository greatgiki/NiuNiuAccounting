package com.example.localledger.capture

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import com.example.localledger.core.storage.AppSettings
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.ai.AiCompleter
import com.example.localledger.domain.capture.BankSmsParser
import com.example.localledger.domain.capture.CaptureFlags
import com.example.localledger.domain.capture.CaptureSelection
import com.example.localledger.domain.capture.CaptureSheet
import com.example.localledger.domain.capture.CategoryHintPrompt
import com.example.localledger.domain.capture.OverlayDeferral
import com.example.localledger.domain.capture.ParsedCapture
import com.example.localledger.domain.capture.PaymentPackages
import com.example.localledger.domain.capture.PaymentScreenParser
import com.example.localledger.domain.capture.ScreenNode
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.time.ZoneIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import android.os.Build

@Singleton
class CaptureCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookkeeper: CaptureBookkeeper,
    private val overlay: CaptureOverlay,
    private val notifier: CaptureNotifier,
    private val settingsStore: SettingsStore,
    private val completer: AiCompleter,
    private val zones: ZoneIdProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val snapshots = ScreenSnapshots()
    private val main = Handler(Looper.getMainLooper())
    private val serviceRef = AtomicReference<AccessibilityService?>(null)
    private val activityRef = AtomicReference<Activity?>(null)
    private val presentMutex = Mutex()

    @Volatile
    private var flags = CaptureFlags()
    private var controller: CaptureSheetController? = null
    private var current: CaptureSheet? = null
    private var deferred: CaptureSheet? = null
    private var quietKey = ""
    private var quietUntil = 0L
    private var shownAt = 0L
    private var watch: Runnable? = null
    private var nullStreak = 0
    private var watchTries = 0
    private var screenJob: Job? = null

    fun flags(): CaptureFlags = flags

    fun snapshotOf(service: AccessibilityService, expectedPackage: String): ScreenNode? {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return null
        val activePackage = try {
            root.packageName?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (activePackage != expectedPackage) {
            recycle(root)
            return null
        }
        return try {
            snapshots.copy(root)
        } finally {
            recycle(root)
        }
    }

    fun warm() {
        scope.launch {
            settingsStore.peek()?.let { apply(it) }
        }
    }

    fun apply(settings: AppSettings) {
        flags = CaptureFlags.from(settings)
        val service = serviceRef.get() ?: return
        if (!flags.master) {
            main.post { clearSheet() }
            notifier.stopForeground(service)
            return
        }
        notifier.startForeground(service)
    }

    fun onConnected(service: AccessibilityService) {
        serviceRef.set(service)
        scope.launch {
            val settings = settingsStore.peek()
            if (settings != null) {
                apply(settings)
            } else if (flags.master) {
                notifier.startForeground(service)
            }
        }
    }

    fun onDisconnected() {
        val service = serviceRef.getAndSet(null)
        if (service != null) notifier.stopForeground(service)
    }

    fun onSnapshot(packageName: String, node: ScreenNode) {
        if (!flags.master || !flags.allows(packageName)) return
        val source = PaymentPackages.sourceOf(packageName) ?: return
        screenJob?.cancel()
        screenJob = scope.launch {
            val parsed = try {
                withContext(Dispatchers.Default) {
                    PaymentScreenParser.parse(source, node, System.currentTimeMillis(), zones.current())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "页面解析失败", error)
                null
            } ?: return@launch
            present(parsed)
        }
    }

    suspend fun bankSmsActive(): Boolean {
        if (!flags.loaded) {
            settingsStore.peek()?.let { apply(it) }
        }
        return flags.master && flags.bankSms
    }

    fun onBankMessage(body: String, receivedAt: Long) {
        if (!flags.loaded) {
            scope.launch {
                settingsStore.peek()?.let { apply(it) }
                if (flags.master && flags.bankSms) parseBank(body, receivedAt)
            }
            return
        }
        if (!flags.master || !flags.bankSms) return
        scope.launch { parseBank(body, receivedAt) }
    }

    fun attachActivity(activity: Activity): Boolean {
        val sheet = deferred ?: current ?: return false
        deferred = null
        stopWatch()
        notifier.cancelWaiting()
        overlay.hide()
        val holder = controllerFor(sheet)
        controller = holder
        current = sheet
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = FrameLayout(activity)
        val margin = (16 * activity.resources.displayMetrics.density).toInt()
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply { setMargins(margin, margin, margin, margin) }
        root.addView(holder.view, params)
        activity.setContentView(root)
        activityRef.set(activity)
        return true
    }

    private suspend fun parseBank(body: String, receivedAt: Long) {
        val parsed = try {
            BankSmsParser.parse(body, receivedAt, zones.current())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "短信解析失败", error)
            null
        } ?: return
        present(parsed)
    }

    private suspend fun present(parsed: ParsedCapture) {
        presentMutex.withLock {
            val now = System.currentTimeMillis()
            val key = fingerprint(parsed)
            if (key == quietKey && now < quietUntil) return
            val showing = current
            if (showing != null && showing.amountMinor == parsed.amountMinor && now - shownAt < 60_000L) return
            val sheet = bookkeeper.prepare(parsed) ?: return
            val themed = sheet.copy(night = night(settingsStore.peek()))
            withContext(Dispatchers.Main) {
                val again = current
                if (again != null && again.amountMinor == themed.amountMinor && System.currentTimeMillis() - shownAt < 60_000L) {
                    return@withContext
                }
                reveal(themed)
            }
            if (themed.aiEligible) classify(themed)
        }
    }

    private fun reveal(sheet: CaptureSheet) {
        current = sheet
        shownAt = System.currentTimeMillis()
        val defer = OverlayDeferral.whilePaymentAppIsOpen(Build.MANUFACTURER, Build.BRAND)
        if (defer || !attachOverlay(sheet)) hold(sheet)
    }

    private fun hold(sheet: CaptureSheet) {
        deferred = sheet
        notifier.showWaiting(sheet)
        watchForLeave()
    }

    private fun attachOverlay(sheet: CaptureSheet): Boolean {
        val holder = controllerFor(sheet)
        if (!overlay.show(holder.view)) return false
        controller = holder
        deferred = null
        notifier.cancelWaiting()
        stopWatch()
        return true
    }

    private fun controllerFor(sheet: CaptureSheet): CaptureSheetController {
        return CaptureSheetController(
            context = context,
            initial = sheet,
            onSave = { selection -> scope.launch { finishSave(sheet, selection) } },
            onLater = { selection -> scope.launch { finishLater(sheet, selection) } },
            onDismiss = { main.post { dismiss(sheet) } },
            onTyping = { typing -> overlay.setTyping(typing) },
        )
    }

    private suspend fun finishSave(sheet: CaptureSheet, selection: CaptureSelection) {
        val result = bookkeeper.save(sheet, selection)
        withContext(Dispatchers.Main) {
            if (!sameSheet(sheet)) return@withContext
            clearSheet()
            overlay.toast(
                when (result) {
                    is BookResult.Saved -> if (result.already) "这笔刚才已经记下了" else "已记下"
                    is BookResult.Drafted -> "没能直接记下，已放进待确认"
                    is BookResult.Failed -> result.message
                },
            )
        }
    }

    private suspend fun finishLater(sheet: CaptureSheet, selection: CaptureSelection) {
        val result = bookkeeper.later(sheet, selection)
        withContext(Dispatchers.Main) {
            if (!sameSheet(sheet)) return@withContext
            clearSheet()
            overlay.toast(
                when (result) {
                    is BookResult.Drafted -> "已放进待确认"
                    is BookResult.Saved -> "已记下"
                    is BookResult.Failed -> result.message
                },
            )
        }
    }

    private fun dismiss(sheet: CaptureSheet) {
        if (!sameSheet(sheet)) return
        quietKey = fingerprint(sheet)
        quietUntil = System.currentTimeMillis() + 45_000L
        clearSheet()
    }

    private fun sameSheet(sheet: CaptureSheet): Boolean {
        return current?.token == sheet.token || deferred?.token == sheet.token
    }

    private fun clearSheet() {
        stopWatch()
        overlay.hide()
        notifier.cancelWaiting()
        controller = null
        current = null
        deferred = null
        activityRef.getAndSet(null)?.let { activity ->
            if (!activity.isFinishing) activity.finish()
        }
    }

    private fun watchForLeave() {
        stopWatch()
        nullStreak = 0
        watchTries = 0
        val task = object : Runnable {
            override fun run() {
                if (deferred == null) return
                watchTries += 1
                when (paymentAppVisible()) {
                    false -> {
                        nullStreak += 1
                        if (nullStreak >= 2) {
                            val sheet = deferred ?: return
                            deferred = null
                            if (!attachOverlay(sheet)) openActivity()
                            return
                        }
                    }
                    true -> nullStreak = 0
                    null -> Unit
                }
                if (watchTries < 40) main.postDelayed(this, 700)
            }
        }
        watch = task
        main.postDelayed(task, 700)
    }

    private fun stopWatch() {
        watch?.let { main.removeCallbacks(it) }
        watch = null
    }

    private fun paymentAppVisible(): Boolean? {
        val service = serviceRef.get() ?: return null
        return try {
            val root = service.rootInActiveWindow ?: return false
            recycle(root)
            true
        } catch (_: Exception) {
            null
        }
    }

    private fun openActivity() {
        try {
            context.startActivity(
                Intent(context, CaptureConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (error: Exception) {
            Log.w(TAG, "打不开确认页：${error.javaClass.simpleName}")
        }
    }

    private fun classify(sheet: CaptureSheet) {
        val choices = if (sheet.type == TransactionType.INCOME) sheet.incomeCategories else sheet.expenseCategories
        if (choices.isEmpty() || sheet.merchant.isBlank()) return
        scope.launch {
            val name = try {
                withTimeout(2_000) {
                    val response = completer.complete(
                        CategoryHintPrompt.request(sheet.merchant, sheet.type, choices.map { it.name }),
                    )
                    CategoryHintPrompt.parse(response.message.text, choices.map { it.name })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@launch
            val id = choices.firstOrNull { it.name == name }?.id ?: return@launch
            withContext(Dispatchers.Main) {
                if (current?.token == sheet.token) controller?.offerCategory(id)
            }
        }
    }

    private fun night(settings: AppSettings?): Boolean {
        return when (settings?.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            else -> {
                val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun fingerprint(parsed: ParsedCapture): String {
        return "${parsed.source}:${parsed.amountMinor}:${parsed.merchant}:${parsed.ruleId}"
    }

    private fun fingerprint(sheet: CaptureSheet): String {
        return "${sheet.source}:${sheet.amountMinor}:${sheet.merchant}:${sheet.ruleId}"
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: android.view.accessibility.AccessibilityNodeInfo) {
        try {
            node.recycle()
        } catch (_: Exception) {
            // Already recycled.
        }
    }

    private companion object {
        const val TAG = "NiuNiuCapture"
    }
}
