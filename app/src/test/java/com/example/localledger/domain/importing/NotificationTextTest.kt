package com.example.localledger.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {
    @Test
    fun plainReadsCharactersInsteadOfToString() {
        val value = object : CharSequence {
            private val raw = "已支付¥12.50"
            override val length: Int = raw.length
            override fun get(index: Int): Char = raw[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = raw.subSequence(startIndex, endIndex)
            override fun toString(): String = "not-the-text"
        }
        assertEquals("已支付¥12.50", NotificationText.plain(value))
    }

    @Test
    fun joinKeepsDistinctPartsInOrder() {
        assertEquals(
            "微信支付\n支付成功\n付款金额¥12.50",
            NotificationText.join(listOf("微信支付", "支付成功", "微信支付", " 付款金额¥12.50 ")),
        )
    }

    @Test
    fun normalizeStripsPrefixesAndFullWidth() {
        assertEquals(
            "微信支付: 微信支付凭证",
            NotificationText.normalize("[2条]微信支付: 微信支付凭证"),
        )
        assertEquals(
            "收款到账12.50元",
            NotificationText.normalize("[店员消息]收款到账１２．５０元"),
        )
        assertEquals(
            "已支付¥1,250.50",
            NotificationText.normalize("已支付￥１，２５０．５０"),
        )
    }
}
