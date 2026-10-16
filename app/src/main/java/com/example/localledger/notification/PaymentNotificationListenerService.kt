package com.example.localledger.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.localledger.data.repository.CapturedNotification
import com.example.localledger.data.repository.PaymentDraftWriter
import com.example.localledger.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var writer: PaymentDraftWriter
    @Inject lateinit var notifier: DraftNotifier
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val event = CapturedNotification(
            packageName = sbn.packageName ?: return,
            text = extractText(sbn),
            postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            key = sbn.key ?: return,
        )
        if (event.text.isBlank()) return
        appScope.launch {
            runCatching {
                when (val result = writer.record(event)) {
                    is com.example.localledger.data.repository.NotificationRecordResult.Created ->
                        notifier.show(result)
                    else -> Unit
                }
            }
        }
    }

    private fun extractText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
            .orEmpty()
        return listOf(title, text, big, lines).filter { it.isNotBlank() }.distinct().joinToString("\n")
    }
}
