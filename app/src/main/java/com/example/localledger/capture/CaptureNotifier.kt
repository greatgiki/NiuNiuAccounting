package com.example.localledger.capture

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.localledger.R
import com.example.localledger.domain.capture.CaptureSheet
import com.example.localledger.domain.money.MinorText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun foregroundNotification(): Notification {
        ensureStatus()
        return NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle("自动记账开着")
            .setContentText("只在本机识别支付结果，不上传屏幕")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(settingsIntent())
            .build()
    }

    fun startForeground(service: Service) {
        val notification = foregroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                service.startForeground(
                    STATUS_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                service.startForeground(STATUS_ID, notification)
            }
        } catch (_: Exception) {
            // Missing the notification permission or a foreground-service type.
        }
    }

    fun stopForeground(service: Service) {
        try {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            NotificationManagerCompat.from(context).cancel(STATUS_ID)
        }
    }

    fun showWaiting(sheet: CaptureSheet) {
        if (!canNotify()) return
        ensureWaiting()
        val who = sheet.merchant.ifBlank { sheet.source.label() }
        val body = "${MinorText.format(sheet.amountMinor)} · $who。支付保护可能会挡住悬浮窗，离开页面后会弹出确认。"
        val notification = NotificationCompat.Builder(context, WAITING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle("已识别一笔付款")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(confirmIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(WAITING_ID, notification)
    }

    fun cancelWaiting() {
        NotificationManagerCompat.from(context).cancel(WAITING_ID)
    }

    private fun settingsIntent(): PendingIntent {
        val intent = Intent(context, com.example.localledger.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 41, intent, pendingFlags())
    }

    private fun confirmIntent(): PendingIntent {
        val intent = Intent(context, CaptureConfirmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 42, intent, pendingFlags())
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureStatus() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(STATUS_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("自动记账")
                .setDescription("自动记账开启时的常驻状态")
                .setSound(null, null)
                .build(),
        )
    }

    private fun ensureWaiting() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(WAITING_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("支付确认")
                .setDescription("离开支付页面后确认这笔记账")
                .build(),
        )
    }

    companion object {
        private const val STATUS_CHANNEL = "capture_status"
        private const val WAITING_CHANNEL = "capture_waiting"
        const val STATUS_ID = 4108
        private const val WAITING_ID = 4109
    }
}
