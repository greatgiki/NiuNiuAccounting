package com.example.localledger.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.localledger.di.ApplicationScope
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
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationListenerHealth.markConnected()
        val cutoff = System.currentTimeMillis() - ImportMatcher.WINDOW_MILLIS
        val active = runCatching { activeNotifications }
            .onFailure { Log.e(TAG, "读取当前通知失败", it) }
            .getOrNull()
            .orEmpty()
            .filter { it.postTime <= 0L || it.postTime >= cutoff }
        launch { active.forEach { notification -> deliver(notification) } }
    }

    override fun onListenerDisconnected() {
        NotificationListenerHealth.markDisconnected()
        super.onListenerDisconnected()
        NotificationRebind.ensure(this)
    }

    override fun onDestroy() {
        NotificationListenerHealth.markDisconnected()
        super.onDestroy()
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
        val event = NotificationEvents.from(sbn) ?: return
        try {
            capture().ingest(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "处理通知失败", error)
        }
    }

    private fun capture(): NotificationCapturePipeline {
        if (::pipeline.isInitialized) return pipeline
        Log.e(TAG, "Hilt 字段未注入，改用 EntryPoint")
        return entryPoint().capturePipeline()
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

    @ApplicationScope
    fun appScope(): CoroutineScope
}
