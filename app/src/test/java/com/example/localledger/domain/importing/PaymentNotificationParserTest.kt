package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationParserTest {
    @Test
    fun tableDrivenPaymentPhrases() {
        val cases = listOf(
            Case(BillSource.WECHAT, "微信支付\n已支付¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "微信支付 已支付￥9.90", TransactionType.EXPENSE, 990L, ""),
            Case(BillSource.WECHAT, "向美团付款¥18.00", TransactionType.EXPENSE, 1_800L, "美团"),
            Case(BillSource.WECHAT, "微信支付收款到账12.50元", TransactionType.INCOME, 1_250L, ""),
            Case(BillSource.WECHAT, "收款到账88.00元", TransactionType.INCOME, 8_800L, ""),
            Case(BillSource.WECHAT, "已收款¥20.00", TransactionType.INCOME, 2_000L, ""),
            Case(BillSource.WECHAT, "收款金额 ¥3.50", TransactionType.INCOME, 350L, ""),
            Case(BillSource.WECHAT, "付款金额¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "付款金额：¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "你已支付¥8.00", TransactionType.EXPENSE, 800L, ""),
            Case(BillSource.WECHAT, "已支付¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "微信支付凭证 ¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "微信支付凭证¥1,250.50", TransactionType.EXPENSE, 125_050L, ""),
            Case(BillSource.WECHAT, "已支付￥１，２５０．５０", TransactionType.EXPENSE, 125_050L, ""),
            Case(BillSource.WECHAT, "[2条]微信支付: 已支付¥12.50", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.WECHAT, "微信收款助手: 微信支付收款0.01元", TransactionType.INCOME, 1L, ""),
            Case(BillSource.WECHAT, "收款到账通知 ¥12.50", TransactionType.INCOME, 1_250L, ""),
            Case(BillSource.WECHAT, "[店员消息]收款到账12.50元", TransactionType.INCOME, 1_250L, ""),
            Case(BillSource.WECHAT, "［店员消息］收款到账通知 ¥0.01", TransactionType.INCOME, 1L, ""),
            Case(BillSource.ALIPAY, "你已成功付款12.50元", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.ALIPAY, "成功付款¥9.90", TransactionType.EXPENSE, 990L, ""),
            Case(BillSource.ALIPAY, "付款成功，12.00元", TransactionType.EXPENSE, 1_200L, ""),
            Case(BillSource.ALIPAY, "你已成功收款20.00元", TransactionType.INCOME, 2_000L, ""),
            Case(BillSource.ALIPAY, "成功收款¥3.50", TransactionType.INCOME, 350L, ""),
            Case(BillSource.ALIPAY, "支付宝成功收款100元", TransactionType.INCOME, 10_000L, ""),
            Case(BillSource.ALIPAY, "你已付款6.60元\n收款方：星巴克", TransactionType.EXPENSE, 660L, "星巴克"),
            Case(BillSource.ALIPAY, "通过扫码向你付款0.01元", TransactionType.INCOME, 1L, ""),
            Case(BillSource.ALIPAY, "向你付款¥6.00", TransactionType.INCOME, 600L, ""),
        )
        cases.forEach { sample ->
            val parsed = PaymentNotificationParser.parse(sample.source, sample.text)
            assertEquals(sample.text, sample.type, parsed?.type)
            assertEquals(sample.text, sample.amountMinor, parsed?.amountMinor)
            assertEquals(sample.text, sample.counterparty, parsed?.counterparty)
            assertEquals(sample.text, false, parsed?.needsAmount)
        }
    }

    @Test
    fun paymentWordingWithoutAmountBecomesNeedsAmount() {
        val samples = listOf(
            BillSource.WECHAT to "微信支付\n微信支付凭证",
            BillSource.WECHAT to "[2条]微信支付: 微信支付凭证",
            BillSource.WECHAT to "微信支付: 支付成功",
            BillSource.WECHAT to "支付成功",
            BillSource.WECHAT to "微信收款助手",
            BillSource.ALIPAY to "你已成功付款",
            BillSource.ALIPAY to "成功收款",
        )
        samples.forEach { (source, text) ->
            val parsed = PaymentNotificationParser.parse(source, text)
            assertEquals(text, true, parsed?.needsAmount)
            assertNull(text, parsed?.amountMinor)
        }
        val voucher = PaymentNotificationParser.parse(BillSource.WECHAT, "[2条]微信支付: 微信支付凭证")
        assertEquals(TransactionType.EXPENSE, voucher?.type)
        val receipt = PaymentNotificationParser.parse(BillSource.WECHAT, "微信收款助手: 微信支付收款")
        assertEquals(TransactionType.INCOME, receipt?.type)
        assertTrue(receipt?.needsAmount == true)
    }

    @Test
    fun chatAndEmptyTextAreIgnored() {
        assertNull(PaymentNotificationParser.parse(BillSource.WECHAT, "张三: 晚上吃饭吗"))
        assertNull(PaymentNotificationParser.parse(BillSource.ALIPAY, "你有一条新消息"))
        assertNull(PaymentNotificationParser.parse(BillSource.WECHAT, ""))
        assertNull(PaymentNotificationParser.parse(BillSource.ALIPAY, ""))
        assertFalse(PaymentNotificationParser.parse(BillSource.WECHAT, "微信支付凭证")?.needsAmount != true)
    }

    @Test
    fun maskedAmountBecomesPendingDraftWithoutUsingStarsAsMoney() {
        val spent = PaymentNotificationParser.parse(BillSource.WECHAT, "微信支付\n你在星巴克消费**元")
        assertEquals(TransactionType.EXPENSE, spent?.type)
        assertNull(spent?.amountMinor)
        assertEquals(true, spent?.needsAmount)
        assertEquals(true, spent?.maskedAmount)
        assertEquals("星巴克", spent?.counterparty)
        assertEquals("wechat-masked", spent?.patternId)
        val paid = PaymentNotificationParser.parse(BillSource.ALIPAY, "向美团付款**.**元")
        assertEquals(TransactionType.EXPENSE, paid?.type)
        assertEquals("美团", paid?.counterparty)
        assertEquals("alipay-masked", paid?.patternId)
        val income = PaymentNotificationParser.parse(BillSource.WECHAT, "收款**元")
        assertEquals(TransactionType.INCOME, income?.type)
        assertEquals(true, income?.maskedAmount)
        val real = PaymentNotificationParser.parse(BillSource.WECHAT, "已支付¥12.50\n消费**元")
        assertEquals(1_250L, real?.amountMinor)
        assertEquals(false, real?.needsAmount)
        assertNull(PaymentNotificationParser.parse(BillSource.WECHAT, "张三: 晚上吃饭吗"))
    }

    private data class Case(
        val source: BillSource,
        val text: String,
        val type: TransactionType,
        val amountMinor: Long,
        val counterparty: String,
    )
}
