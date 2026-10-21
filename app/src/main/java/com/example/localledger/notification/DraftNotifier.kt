package com.example.localledger.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.localledger.MainActivity
import com.example.localledger.data.repository.NotificationRecordResult
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.MoneyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class DraftNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(result: NotificationRecordResult.Created) {
        if (!canNotify()) return
        ensureChannel()
        val typeLabel = if (result.type == TransactionType.INCOME) "收入" else "支出"
        val amount = if (result.needsAmount) "需补全金额" else MoneyFormatter.format(result.amountMinor)
        val who = result.counterparty.ifBlank { result.source.label() }
        val body = when {
            result.needsAmount -> "$typeLabel · 需补全金额 · $who，打开应用补上金额后再确认"
            result.autoConfirmed -> "已自动记一笔$typeLabel $amount · $who"
            else -> "$typeLabel $amount · $who，点按确认或修改"
        }
        val open = pendingActivity(result.draftId.hashCode())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.localledger.R.drawable.ic_stat_ledger)
            .setContentTitle(if (result.autoConfirmed) "已自动记账" else "待确认记账")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (!result.autoConfirmed && !result.needsAmount) {
            builder.addAction(com.example.localledger.R.drawable.ic_stat_ledger, "确认", pendingBroadcast(DraftActionReceiver.ACTION_CONFIRM, result.draftId))
            builder.addAction(com.example.localledger.R.drawable.ic_stat_ledger, "忽略", pendingBroadcast(DraftActionReceiver.ACTION_DISMISS, result.draftId))
        }
        notifyIfAllowed(result.draftId.hashCode(), builder.build())
    }

    fun showEnriched(result: NotificationRecordResult.Enriched) {
        if (!canNotify()) return
        ensureChannel()
        val typeLabel = if (result.type == TransactionType.INCOME) "收入" else "支出"
        val amount = MoneyFormatter.format(result.amountMinor)
        val who = result.counterparty.ifBlank { result.source.label() }
        val body = if (result.autoConfirmed) {
            "已补全金额并自动记一笔$typeLabel $amount · $who"
        } else {
            "已补全金额，$typeLabel $amount · $who，点按确认或修改"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.localledger.R.drawable.ic_stat_ledger)
            .setContentTitle(if (result.autoConfirmed) "已自动记账" else "待确认记账")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingActivity(result.draftId.hashCode()))
            .setAutoCancel(true)
        if (!result.autoConfirmed) {
            builder.addAction(com.example.localledger.R.drawable.ic_stat_ledger, "确认", pendingBroadcast(DraftActionReceiver.ACTION_CONFIRM, result.draftId))
            builder.addAction(com.example.localledger.R.drawable.ic_stat_ledger, "忽略", pendingBroadcast(DraftActionReceiver.ACTION_DISMISS, result.draftId))
        }
        notifyIfAllowed(result.draftId.hashCode(), builder.build())
    }

    fun showNeedAmount(draftId: String) {
        if (!canNotify()) return
        ensureChannel()
        val body = "这笔待确认记账还缺金额，打开应用补上金额后再确认。"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.localledger.R.drawable.ic_stat_ledger)
            .setContentTitle("需要补全金额")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingActivity(draftId.hashCode()))
            .setAutoCancel(true)
            .build()
        notifyIfAllowed(draftId.hashCode(), notification)
    }

    fun showNeedAssignment(draftId: String) {
        if (!canNotify()) return
        ensureChannel()
        val body = "这笔待确认记账还缺分类或账户，打开应用补全后再确认。"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.localledger.R.drawable.ic_stat_ledger)
            .setContentTitle("需要补全后才能记账")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingActivity(draftId.hashCode()))
            .setAutoCancel(true)
            .build()
        notifyIfAllowed(draftId.hashCode(), notification)
    }

    fun cancel(draftId: String) {
        NotificationManagerCompat.from(context).cancel(draftId.hashCode())
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("待确认记账")
                .setDescription("微信和支付宝付款通知生成的待确认流水")
                .build(),
        )
    }

    private fun pendingActivity(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_DRAFTS, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingBroadcast(action: String, draftId: String): PendingIntent {
        val intent = Intent(context, DraftActionReceiver::class.java).apply {
            this.action = action
            setPackage(context.packageName)
            putExtra(DraftActionReceiver.EXTRA_DRAFT_ID, draftId)
        }
        return PendingIntent.getBroadcast(
            context,
            (action + draftId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "ledger_drafts"
    }
}
