package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.statistics.TrendFill
import com.example.localledger.domain.model.MonthlyTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class CurrencyAndRatioTest {
    private val snapshot = ExchangeRateSnapshot(
        baseCode = "CNY",
        quotesPerUnit = mapOf(
            "USD" to BigDecimal("0.14"),
            "JPY" to BigDecimal("20"),
            "EUR" to BigDecimal("0.13"),
        ),
        fetchedAt = 1L,
        source = "test",
    )

    @Test
    fun sameCurrencyDoesNotNeedARate() {
        assertEquals(12_345L, CurrencyConverter.toBaseMinor(12_345, "CNY", "CNY", snapshot = null))
    }

    @Test
    fun usdCentsConvertToCnyFen() {
        assertEquals(714L, CurrencyConverter.toBaseMinor(100, "USD", "CNY", snapshot))
    }

    @Test
    fun jpyUsesZeroDecimalScale() {
        assertEquals(500L, CurrencyConverter.toBaseMinor(100, "JPY", "CNY", snapshot))
    }

    @Test
    fun transferBetweenForeignCurrenciesUsesTheLedgerTable() {
        val eurMinor = CurrencyConverter.betweenAccounts(100, "USD", "EUR", "CNY", snapshot)
        assertEquals(93L, eurMinor)
    }

    @Test(expected = LedgerException.ExchangeRateUnavailable::class)
    fun missingQuoteFailsClosed() {
        CurrencyConverter.toBaseMinor(100, "GBP", "CNY", snapshot)
    }

    @Test
    fun ratioDoesNotCollapseLargeLongs() {
        val part = 9_000_000_000_000_001L
        val total = 18_000_000_000_000_002L
        assertEquals(0.5f, RatioMath.fraction(part, total))
        assertEquals("50.0%", RatioMath.percentLabel(part, total))
    }

    @Test
    fun longCustomRangeFillsEmptyMonths() {
        val filled = TrendFill.fillMonths(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 4),
            listOf(MonthlyTotal(YearMonth.of(2026, 1), 10, 3), MonthlyTotal(YearMonth.of(2026, 3), 0, 8)),
        )
        assertEquals(
            listOf(
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 3),
                YearMonth.of(2026, 4),
            ),
            filled.map { it.yearMonth },
        )
        assertEquals(0L, filled[1].incomeMinor)
        assertEquals(8L, filled[2].expenseMinor)
    }
}
