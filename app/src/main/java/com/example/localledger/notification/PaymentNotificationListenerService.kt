package com.example.localledger.notification

import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.localledger.capture.CaptureCoordinator
import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.capture.BankPackages
import com.example.localledger.domain.capture.SmsPackages
import com.example.localledger.domain.importing.ImportMatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var pipeline: NotificationCapturePipeline
    @Inject lateinit var captureCoordinator: CaptureCoordinator
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onListenerConnected() {
        super.onListenerConnected()
        SourceBillIntents.attach(this)
        NotificationListenerHealth.markConnected()
        val cutoff = System.currentTimeMillis() - ImportMatcher.WINDOW_MILLIS
        val active = runCatching { activeNotifications }
            .onFailure { Log.e(TAG, "读取当前通知失败", it) }
            .getOrNull()
        SourceBillIntents.rememberActive(active)
        val recent = active.orEmpty().filter { it.postTime <= 0L || it.postTime >= cutoff }
        launch { recent.forEach { notification -> deliver(notification) } }
    }

    override fun onListenerDisconnected() {
        SourceBillIntents.detach(this)
        NotificationListenerHealth.markDisconnected()
        super.onListenerDisconnected()
        NotificationRebind.ensure(this)
    }

    override fun onDestroy() {
        SourceBillIntents.detach(this)
        NotificationListenerHealth.markDisconnected()
        super.onDestroy()
    }

    fun findContentIntent(key: String): PendingIntent? {
        val active = runCatching { activeNotifications }.getOrNull() ?: return null
        return active.firstOrNull { it.key == key }?.notification?.contentIntent
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        launch { deliver(sbn) }
    }

    private fun launch(block: suspend () -> Unit) {
        val scope = runCatching { scope() }.getOrElse {
            Log.e(TAG, "无法取得协程作用域", it)
            return
        }
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "通知协程失败", error)
            }
        }
    }

    private suspend fun deliver(sbn: StatusBarNotification) {
        if (SmsPackages.isSms(sbn.packageName) || BankPackages.isBankApp(sbn.packageName)) {
            deliverSms(sbn)
            return
        }
        sbn.notification?.contentIntent?.let { intent ->
            val key = sbn.key
            if (!key.isNullOrBlank()) SourceBillIntents.remember(key, intent)
        }
        val event = NotificationEvents.from(sbn) ?: return
        try {
            capture().ingest(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "处理通知失败", error)
        }
    }

    private suspend fun deliverSms(sbn: StatusBarNotification) {
        if (!coordinator().bankSmsActive()) return
        val text = sbn.notification?.let { NotificationTextExtractor.extract(it) }.orEmpty()
        if (text.isBlank()) return
        val posted = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        coordinator().onBankMessage(text, posted)
    }

    private fun capture(): NotificationCapturePipeline {
        if (::pipeline.isInitialized) return pipeline
        Log.e(TAG, "Hilt 字段未注入，改用 EntryPoint")
        return entryPoint().capturePipeline()
    }

    private fun coordinator(): CaptureCoordinator {
        if (::captureCoordinator.isInitialized) return captureCoordinator
        return entryPoint().captureCoordinator()
    }

    private fun scope(): CoroutineScope {
        if (::appScope.isInitialized) return appScope
        return entryPoint().appScope()
    }

    private fun entryPoint(): NotificationListenerEntryPoint {
        return EntryPointAccessors.fromApplication(applicationContext, NotificationListenerEntryPoint::class.java)
    }

    companion object {
        private const val TAG = "NiuNiuNotify"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationListenerEntryPoint {
    fun capturePipeline(): NotificationCapturePipeline

    fun captureCoordinator(): CaptureCoordinator

    @ApplicationScope
    fun appScope(): CoroutineScope
}
