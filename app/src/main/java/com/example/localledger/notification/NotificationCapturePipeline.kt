package com.example.localledger.notification

import android.util.Log
import com.example.localledger.data.repository.CapturedNotification
import com.example.localledger.data.repository.NotificationRecordResult
import com.example.localledger.data.repository.PaymentDraftWriter
import com.example.localledger.data.repository.describe
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCapturePipeline @Inject constructor(
    private val writer: PaymentDraftWriter,
    private val notifier: DraftNotifier,
    private val diagnostics: NotificationDiagnosticsStore,
) {
    suspend fun ingest(event: CapturedNotification): NotificationRecordResult {
        val result = try {
            writer.record(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "通知记账失败", error)
            NotificationRecordResult.Failed(error.message ?: "未知错误")
        }
        diagnostics.record(event, result)
        publish(result)
        Log.i(TAG, "通知处理 ${event.packageName} ${result.describe()}")
        return result
    }

    suspend fun simulateSample(): NotificationRecordResult {
        val now = System.currentTimeMillis()
        val event = CapturedNotification(
            packageName = PaymentDraftWriter.WECHAT_PACKAGE,
            text = "微信支付\n微信支付凭证\n付款金额¥12.50",
            postedAt = now,
            key = "debug-sample-$now",
            postTime = now,
            groupKey = "debug-sample",
            isGroupSummary = false,
        )
        return ingest(event)
    }

    private fun publish(result: NotificationRecordResult) {
        when (result) {
            is NotificationRecordResult.Created -> notifier.show(result)
            is NotificationRecordResult.Enriched -> notifier.showEnriched(result)
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "NiuNiuNotify"
    }
}
