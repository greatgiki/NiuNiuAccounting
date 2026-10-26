package com.example.localledger.domain.importing

import com.example.localledger.core.storage.AppSettings
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {
    @Test
    fun defaultsLeaveMasterOffAndAppsOn() {
        val defaults = AppSettings(currentLedgerId = "ledger")
        assertFalse(defaults.autoRecordEnabled)
        assertFalse(defaults.autoConfirmDrafts)
        assertTrue(defaults.wechatNotificationEnabled)
        assertTrue(defaults.alipayNotificationEnabled)
        assertTrue(defaults.keepAmountlessDrafts)
        val gate = NotificationCapturePolicy.gate(
            NotificationCapturePolicy.WECHAT_PACKAGE,
            CaptureSettings.from(defaults),
        )
        assertEquals(CaptureGate.Disabled("自动记账未开启"), gate)
        assertFalse(defaults.bankSmsEnabled)
        assertTrue(defaults.unionPayEnabled)
        assertTrue(defaults.notificationFallbackEnabled)
        assertEquals("", defaults.captureLedgerId)
    }

    @Test
    fun notificationFallbackCanBeTurnedOff() {
        val gate = NotificationCapturePolicy.gate(
            NotificationCapturePolicy.WECHAT_PACKAGE,
            enabled().copy(notificationFallbackEnabled = false),
        )
        assertEquals(CaptureGate.Disabled("通知补充已关闭"), gate)
    }

    @Test
    fun oldSettingsJsonKeepsCaptureDefaults() {
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<AppSettings>("""{"currentLedgerId":"ledger","autoRecordEnabled":true}""")
        assertTrue(decoded.autoRecordEnabled)
        assertFalse(decoded.bankSmsEnabled)
        assertTrue(decoded.notificationFallbackEnabled)
        assertTrue(decoded.learnedAccountByKey.isEmpty())
    }

    @Test
    fun unreadSettingsDoNotLookLikeTogglesOff() {
        val gate = NotificationCapturePolicy.gate("com.tencent.mm", CaptureSettings.from(null))
        assertEquals(CaptureGate.Disabled("设置尚未读取"), gate)
    }

    @Test
    fun packageFilterIsExact() {
        val settings = enabled()
        assertTrue(NotificationCapturePolicy.gate("com.tencent.mm", settings) is CaptureGate.Open)
        assertTrue(NotificationCapturePolicy.gate("com.eg.android.AlipayGphone", settings) is CaptureGate.Open)
        assertEquals(
            CaptureGate.Ignored("不是微信或支付宝"),
            NotificationCapturePolicy.gate("com.tencent.mm.plugin", settings),
        )
        assertEquals(
            CaptureGate.Ignored("不是微信或支付宝"),
            NotificationCapturePolicy.gate("com.tencent.mm ", settings),
        )
        assertEquals(
            CaptureGate.Ignored("不是微信或支付宝"),
            NotificationCapturePolicy.gate("com.eg.android.alipaygphone", settings),
        )
        val wechatOff = settings.copy(wechatEnabled = false)
        assertEquals(CaptureGate.Disabled("微信开关已关闭"), NotificationCapturePolicy.gate("com.tencent.mm", wechatOff))
        val alipayOff = settings.copy(alipayEnabled = false)
        assertEquals(
            CaptureGate.Disabled("支付宝开关已关闭"),
            NotificationCapturePolicy.gate("com.eg.android.AlipayGphone", alipayOff),
        )
    }

    @Test
    fun groupSummaryOngoingAndCategoryAreStillRead() {
        assertTrue(NotificationCapturePolicy.shouldRead(isGroupSummary = true, isOngoing = false, category = "msg"))
        assertTrue(NotificationCapturePolicy.shouldRead(isGroupSummary = false, isOngoing = true, category = null))
        assertTrue(NotificationCapturePolicy.shouldRead(isGroupSummary = true, isOngoing = true, category = "progress"))
    }

    @Test
    fun amountlessDraftsCanBeTurnedOff() {
        val settings = enabled()
        assertTrue(NotificationCapturePolicy.amountlessAllowed(needsAmount = true, settings))
        assertFalse(NotificationCapturePolicy.amountlessAllowed(needsAmount = true, settings.copy(keepAmountless = false)))
        assertTrue(NotificationCapturePolicy.amountlessAllowed(needsAmount = false, settings.copy(keepAmountless = false)))
    }

    @Test
    fun samePostingKeepsTheFirstRecord() {
        val first = stored(key = "sbn", postTime = 1_000L, createdAt = 1_000L)
        val again = incoming(key = "sbn", postTime = 1_000L, now = 1_100L)
        assertEquals(DedupDecision.KeepFirst, NotificationCapturePolicy.dedup(again, listOf(first)))
    }

    @Test
    fun legacyKeyWithoutPostTimeStillKeepsTheOriginalPosting() {
        val legacy = stored(key = "sbn", postTime = 5_000L, createdAt = 5_000L).copy(
            dedupKey = "sbn",
            sbnKey = "sbn",
        )
        val replay = incoming(key = "sbn", postTime = 5_000L, now = 9_000L)
        assertEquals(DedupDecision.KeepFirst, NotificationCapturePolicy.dedup(replay, listOf(legacy)))
    }

    @Test
    fun tenMinuteSameAmountDoesNotDropALaterPayment() {
        val now = 1_000_000L
        val earlier = stored(key = "older", postTime = now - 5 * 60_000L, createdAt = now - 5 * 60_000L, amount = 1_250L)
        val decision = NotificationCapturePolicy.dedup(
            incoming(key = "newer", postTime = now, now = now, amount = 1_250L),
            listOf(earlier),
        )
        assertEquals(DedupDecision.Insert(likelyDuplicate = true), decision)
    }

    @Test
    fun burstRepostDoesNotCreateASecondDraft() {
        val now = 50_000L
        val first = stored(key = "child", postTime = now - 1_000L, createdAt = now - 2_000L, amount = 1_250L)
        val summary = incoming(key = "summary", postTime = now, now = now, amount = 1_250L)
        assertEquals(DedupDecision.KeepFirst, NotificationCapturePolicy.dedup(summary, listOf(first)))
    }

    @Test
    fun sameKeyUpdateFillsTheAmountlessDraftInsteadOfDroppingIt() {
        val placeholder = stored(key = "sbn", postTime = 10L, createdAt = 10L, needsAmount = true, amount = 0L)
        val update = incoming(key = "sbn", postTime = 80L, now = 90L, amount = 1_250L)
        val decision = NotificationCapturePolicy.dedup(update, listOf(placeholder))
        assertEquals(DedupDecision.Enrich(placeholder.id, likelyDuplicate = false), decision)
    }

    @Test
    fun secondAmountlessPlaceholderForTheSameNotificationIsKeptAsTheFirst() {
        val placeholder = stored(key = "sbn", postTime = 10L, createdAt = 10L, needsAmount = true, amount = 0L)
        val again = incoming(key = "sbn", postTime = 40L, now = 40L, needsAmount = true, amount = null)
        assertEquals(DedupDecision.KeepFirst, NotificationCapturePolicy.dedup(again, listOf(placeholder)))
    }

    @Test
    fun repostOfTheSamePostingFillsAmountWithoutReplacingTheFirst() {
        val placeholder = stored(key = "sbn", postTime = 1_000L, createdAt = 1_000L, needsAmount = true, amount = 0L)
        val repost = incoming(key = "sbn", postTime = 1_000L, now = 1_200L, amount = 1_250L)
        val decision = NotificationCapturePolicy.dedup(repost, listOf(placeholder))
        assertEquals(DedupDecision.Enrich(placeholder.id, likelyDuplicate = false), decision)
    }

    @Test
    fun freshNotificationIsInserted() {
        val decision = NotificationCapturePolicy.dedup(incoming(key = "sbn", postTime = 1_000L), emptyList())
        assertEquals(DedupDecision.Insert(likelyDuplicate = false), decision)
    }

    private fun enabled() = CaptureSettings(
        loaded = true,
        autoRecordEnabled = true,
        wechatEnabled = true,
        alipayEnabled = true,
        keepAmountless = true,
        autoConfirm = false,
    )

    private fun incoming(
        key: String,
        postTime: Long,
        now: Long = postTime,
        amount: Long? = 1_250L,
        needsAmount: Boolean = false,
        group: String? = null,
    ) = DedupIncoming(
        dedupKey = NotificationCapturePolicy.dedupKey(key, postTime),
        sbnKey = key,
        postTime = postTime,
        groupKey = group,
        type = TransactionType.EXPENSE,
        amountMinor = amount,
        needsAmount = needsAmount,
        now = now,
    )

    private fun stored(
        key: String,
        postTime: Long,
        createdAt: Long,
        amount: Long = 1_250L,
        needsAmount: Boolean = false,
        group: String? = null,
    ) = DedupExisting(
        id = "draft-$key-$postTime",
        dedupKey = NotificationCapturePolicy.dedupKey(key, postTime),
        sbnKey = key,
        postTime = postTime,
        groupKey = group,
        type = TransactionType.EXPENSE,
        amountMinor = amount,
        needsAmount = needsAmount,
        pending = true,
        createdAt = createdAt,
    )
}
