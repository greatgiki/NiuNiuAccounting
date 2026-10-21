package com.example.localledger.notification

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationEventsTest {
    @Test
    fun groupSummaryStillExposesEveryTextField() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val style = Notification.MessagingStyle("微信支付")
        style.addMessage(SpannableString("付款金额¥12.50"), 10L, "微信支付")
        val notification = Notification.Builder(context, "ledger")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(SpannableString("微信支付"))
            .setContentText(SpannableString("支付成功"))
            .setSubText("副文本")
            .setTicker("微信支付: 已支付¥1.00")
            .setStyle(style)
            .build()
        notification.flags = notification.flags or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, SpannableString("微信支付"))
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, SpannableString("支付成功"))
        notification.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "大文本¥2.00")
        notification.extras.putCharSequence(Notification.EXTRA_SUB_TEXT, "副文本")
        notification.extras.putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "摘要")
        notification.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("行一"))
        notification.tickerText = "微信支付: 已支付¥1.00"
        val text = NotificationTextExtractor.extract(notification)
        assertTrue(text, text.contains("微信支付"))
        assertTrue(text, text.contains("支付成功"))
        assertTrue(text, text.contains("付款金额¥12.50"))
        assertTrue(text, text.contains("副文本"))
        assertTrue(text, text.contains("摘要"))
        assertTrue(text, text.contains("行一"))
        assertTrue(text, text.contains("大文本¥2.00"))
        assertTrue(text, text.contains("已支付¥1.00"))

        val posted = StatusBarNotification(
            "com.tencent.mm",
            "com.tencent.mm",
            7,
            "wxpay",
            Process.myUid(),
            Process.myPid(),
            0,
            notification,
            Process.myUserHandle(),
            1_700_000_000_000L,
        )
        val event = NotificationEvents.from(posted)
        assertEquals("com.tencent.mm", event?.packageName)
        assertTrue(event?.isGroupSummary == true)
        assertTrue(event?.text?.contains("付款金额¥12.50") == true)
        assertTrue(event?.text?.contains("支付成功") == true)
    }
}
