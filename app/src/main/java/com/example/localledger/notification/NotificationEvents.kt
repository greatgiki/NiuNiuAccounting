package com.example.localledger.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.localledger.data.repository.CapturedNotification
import com.example.localledger.domain.importing.NotificationCapturePolicy

object NotificationEvents {
    fun from(sbn: StatusBarNotification, now: Long = System.currentTimeMillis()): CapturedNotification? {
        val packageName = sbn.packageName ?: return null
        if (NotificationCapturePolicy.paymentSource(packageName) == null) return null
        val key = sbn.key ?: return null
        val notification = sbn.notification ?: return null
        val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (!NotificationCapturePolicy.shouldRead(summary, ongoing, notification.category)) return null
        val postTime = sbn.postTime.takeIf { it > 0L } ?: now
        return CapturedNotification(
            packageName = packageName,
            text = NotificationTextExtractor.extract(notification),
            postedAt = postTime,
            key = key,
            postTime = postTime,
            groupKey = sbn.groupKey,
            isGroupSummary = summary,
        )
    }
}
