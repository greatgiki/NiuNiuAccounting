package com.example.localledger.core.money

import com.example.localledger.domain.error.LedgerException
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParserTest {
    @Test
    fun parsesYuanWithoutFloatingPoint() {
        assertEquals(1L, MoneyParser.yuanToMinor("0.01"))
        assertEquals(1_250L, MoneyParser.yuanToMinor("12.50"))
        assertEquals(10_000L, MoneyParser.yuanToMinor("100"))
    }

    @Test(expected = LedgerException.InvalidAmount::class)
    fun rejectsMoreThanTwoDecimalPlaces() {
        MoneyParser.yuanToMinor("1.001")
    }

    @Test(expected = LedgerException.InvalidAmount::class)
    fun rejectsLongOverflow() {
        MoneyParser.yuanToMinor("92233720368547758.08")
    }

    @Test(expected = LedgerException.InvalidAmount::class)
    fun rejectsZero() {
        MoneyParser.yuanToMinor("0")
    }
}

