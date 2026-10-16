package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            Case(BillSource.ALIPAY, "你已成功付款12.50元", TransactionType.EXPENSE, 1_250L, ""),
            Case(BillSource.ALIPAY, "成功付款¥9.90", TransactionType.EXPENSE, 990L, ""),
            Case(BillSource.ALIPAY, "付款成功，12.00元", TransactionType.EXPENSE, 1_200L, ""),
            Case(BillSource.ALIPAY, "你已成功收款20.00元", TransactionType.INCOME, 2_000L, ""),
            Case(BillSource.ALIPAY, "成功收款¥3.50", TransactionType.INCOME, 350L, ""),
            Case(BillSource.ALIPAY, "支付宝成功收款100元", TransactionType.INCOME, 10_000L, ""),
            Case(BillSource.ALIPAY, "你已付款6.60元\n收款方：星巴克", TransactionType.EXPENSE, 660L, "星巴克"),
        )
        cases.forEach { sample ->
            val parsed = PaymentNotificationParser.parse(sample.source, sample.text)
            assertEquals(sample.text, sample.type, parsed?.type)
            assertEquals(sample.text, sample.amountMinor, parsed?.amountMinor)
            assertEquals(sample.text, sample.counterparty, parsed?.counterparty)
        }
    }

    @Test
    fun chatAndEmptyTextAreIgnored() {
        assertNull(PaymentNotificationParser.parse(BillSource.WECHAT, "张三: 晚上吃饭吗"))
        assertNull(PaymentNotificationParser.parse(BillSource.ALIPAY, "你有一条新消息"))
        assertNull(PaymentNotificationParser.parse(BillSource.WECHAT, ""))
        assertNull(PaymentNotificationParser.parse(BillSource.ALIPAY, "你已成功付款"))
    }

    private data class Case(
        val source: BillSource,
        val text: String,
        val type: TransactionType,
        val amountMinor: Long,
        val counterparty: String,
    )
}
