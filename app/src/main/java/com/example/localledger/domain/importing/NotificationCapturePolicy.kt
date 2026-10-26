package com.example.localledger.domain.importing

import com.example.localledger.core.storage.AppSettings
import com.example.localledger.domain.model.TransactionType
import kotlin.math.abs

data class CaptureSettings(
    val loaded: Boolean,
    val autoRecordEnabled: Boolean,
    val wechatEnabled: Boolean,
    val alipayEnabled: Boolean,
    val keepAmountless: Boolean,
    val autoConfirm: Boolean,
    val unionPayEnabled: Boolean = true,
    val bankSmsEnabled: Boolean = false,
    val notificationFallbackEnabled: Boolean = true,
) {
    companion object {
        fun from(settings: AppSettings?): CaptureSettings {
            if (settings == null) {
                return CaptureSettings(
                    loaded = false,
                    autoRecordEnabled = false,
                    wechatEnabled = true,
                    alipayEnabled = true,
                    keepAmountless = true,
                    autoConfirm = false,
                )
            }
            return CaptureSettings(
                loaded = true,
                autoRecordEnabled = settings.autoRecordEnabled,
                wechatEnabled = settings.wechatNotificationEnabled,
                alipayEnabled = settings.alipayNotificationEnabled,
                keepAmountless = settings.keepAmountlessDrafts,
                autoConfirm = settings.autoConfirmDrafts,
                unionPayEnabled = settings.unionPayEnabled,
                bankSmsEnabled = settings.bankSmsEnabled,
                notificationFallbackEnabled = settings.notificationFallbackEnabled,
            )
        }
    }
}

sealed interface CaptureGate {
    data class Open(val source: BillSource) : CaptureGate
    data class Disabled(val reason: String) : CaptureGate
    data class Ignored(val reason: String) : CaptureGate
}

data class DedupIncoming(
    val dedupKey: String,
    val sbnKey: String,
    val postTime: Long,
    val groupKey: String?,
    val type: TransactionType,
    val amountMinor: Long?,
    val needsAmount: Boolean,
    val now: Long,
)

data class DedupExisting(
    val id: String,
    val dedupKey: String,
    val sbnKey: String,
    val postTime: Long,
    val groupKey: String?,
    val type: TransactionType,
    val amountMinor: Long,
    val needsAmount: Boolean,
    val pending: Boolean,
    val createdAt: Long,
)

sealed interface DedupDecision {
    data class Insert(val likelyDuplicate: Boolean) : DedupDecision
    data object KeepFirst : DedupDecision
    data class Enrich(val draftId: String, val likelyDuplicate: Boolean) : DedupDecision
}

object NotificationCapturePolicy {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    const val BURST_WINDOW_MILLIS = 15_000L
    const val GROUP_PLACEHOLDER_WINDOW_MILLIS = 120_000L

    fun paymentSource(packageName: String): BillSource? = when (packageName) {
        WECHAT_PACKAGE -> BillSource.WECHAT
        ALIPAY_PACKAGE -> BillSource.ALIPAY
        else -> null
    }

    fun dedupKey(sbnKey: String, postTime: Long): String = "$sbnKey\u001F$postTime"

    fun gate(packageName: String, settings: CaptureSettings): CaptureGate {
        if (!settings.loaded) return CaptureGate.Disabled("设置尚未读取")
        val source = paymentSource(packageName) ?: return CaptureGate.Ignored("不是微信或支付宝")
        if (!settings.autoRecordEnabled) return CaptureGate.Disabled("自动记账未开启")
        if (!settings.notificationFallbackEnabled) return CaptureGate.Disabled("通知补充已关闭")
        if (source == BillSource.WECHAT && !settings.wechatEnabled) return CaptureGate.Disabled("微信开关已关闭")
        if (source == BillSource.ALIPAY && !settings.alipayEnabled) return CaptureGate.Disabled("支付宝开关已关闭")
        return CaptureGate.Open(source)
    }

    fun amountlessAllowed(needsAmount: Boolean, settings: CaptureSettings): Boolean {
        return !needsAmount || settings.keepAmountless
    }

    /**
     * Group summaries, ongoing rows, and categories are not reasons to drop.
     * The WeChat shade entry the user actually sees is often FLAG_GROUP_SUMMARY.
     */
    fun shouldRead(isGroupSummary: Boolean, isOngoing: Boolean, category: String?): Boolean {
        val blocked = false
        if (blocked && isGroupSummary) return false
        if (blocked && isOngoing) return false
        if (blocked && category == "progress") return false
        return true
    }

    fun dedup(incoming: DedupIncoming, existing: List<DedupExisting>): DedupDecision {
        val samePosting = existing.filter { samePosting(it, incoming) }
        if (samePosting.isNotEmpty()) {
            val enrichTarget = samePosting.firstOrNull { row ->
                !incoming.needsAmount && row.pending && row.needsAmount && row.type == incoming.type
            }
            if (enrichTarget != null) {
                return DedupDecision.Enrich(enrichTarget.id, likelyDuplicate(incoming, existing, enrichTarget.id))
            }
            return DedupDecision.KeepFirst
        }
        if (!incoming.needsAmount) {
            val enrichTarget = existing.firstOrNull { row ->
                row.pending && row.needsAmount && row.type == incoming.type && sameConversation(row, incoming)
            }
            if (enrichTarget != null) {
                return DedupDecision.Enrich(enrichTarget.id, likelyDuplicate(incoming, existing, enrichTarget.id))
            }
        } else {
            val placeholder = existing.firstOrNull { row ->
                row.pending && row.needsAmount && row.type == incoming.type && sameConversation(row, incoming)
            }
            if (placeholder != null) return DedupDecision.KeepFirst
        }
        val amount = incoming.amountMinor
        if (!incoming.needsAmount && amount != null) {
            val burst = existing.any { row ->
                !row.needsAmount &&
                    row.type == incoming.type &&
                    row.amountMinor == amount &&
                    row.dedupKey != incoming.dedupKey &&
                    abs(incoming.now - row.createdAt) <= BURST_WINDOW_MILLIS
            }
            if (burst) return DedupDecision.KeepFirst
        }
        return DedupDecision.Insert(likelyDuplicate(incoming, existing, exceptId = null))
    }

    private fun samePosting(row: DedupExisting, incoming: DedupIncoming): Boolean {
        if (row.dedupKey == incoming.dedupKey) return true
        return row.dedupKey == incoming.sbnKey && row.postTime == incoming.postTime
    }

    private fun sameConversation(row: DedupExisting, incoming: DedupIncoming): Boolean {
        if (row.sbnKey.isNotBlank() && row.sbnKey == incoming.sbnKey) return true
        val group = incoming.groupKey
        return !group.isNullOrBlank() &&
            row.groupKey == group &&
            incoming.now - row.createdAt <= GROUP_PLACEHOLDER_WINDOW_MILLIS
    }

    private fun likelyDuplicate(incoming: DedupIncoming, existing: List<DedupExisting>, exceptId: String?): Boolean {
        val amount = incoming.amountMinor ?: return false
        if (incoming.needsAmount) return false
        return existing.any { row ->
            row.id != exceptId &&
                !row.needsAmount &&
                row.type == incoming.type &&
                row.amountMinor == amount &&
                row.dedupKey != incoming.dedupKey &&
                abs(row.postTime - incoming.postTime) <= ImportMatcher.WINDOW_MILLIS
        }
    }
}
