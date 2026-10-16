package com.example.localledger.domain.money

import com.example.localledger.domain.model.ExchangeRateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class CfetsCcprTest {
    @Test
    fun latestFileKeepsDirectPerHundredAndInverseQuotes() {
        val parsed = CfetsCcpr.parseLatest(LATEST_FIXTURE)
        assertEquals(publishedAt(), parsed.publishedAtMillis)
        assertEquals(CfetsCcpr.SOURCE, parsed.source)
        assertEquals(0, divided("1", "6.7220").compareTo(parsed.foreignPerOneCny.getValue("USD")))
        assertEquals(0, divided("100", "4.2439").compareTo(parsed.foreignPerOneCny.getValue("JPY")))
        assertEquals(0, BigDecimal("200.28").compareTo(parsed.foreignPerOneCny.getValue("KRW")))
        assertEquals(0, divided("1", "7.5228").compareTo(parsed.foreignPerOneCny.getValue("EUR")))
        assertFalse(parsed.foreignPerOneCny.containsKey("NZD"))
        assertFalse(parsed.foreignPerOneCny.containsKey("THB"))
    }

    @Test
    fun historyRowWithoutClockUsesNineFifteenBeijing() {
        val parsed = CfetsCcpr.parseHistory(HISTORY_FIXTURE)
        assertEquals(publishedAt(), parsed.publishedAtMillis)
        assertEquals(0, divided("100", "4.2439").compareTo(parsed.foreignPerOneCny.getValue("JPY")))
        assertEquals(0, BigDecimal("200.28").compareTo(parsed.foreignPerOneCny.getValue("KRW")))
    }

    @Test
    fun cnyBaseAndCrossBaseUseThePublishedParity() {
        val parsed = CfetsCcpr.parseLatest(LATEST_FIXTURE)
        val cny = snapshot(parsed, "CNY")
        assertEquals(672L, CurrencyConverter.toBaseMinor(100, "USD", "CNY", cny))
        assertEquals(424L, CurrencyConverter.toBaseMinor(100, "JPY", "CNY", cny))
        assertEquals(10_000L, CurrencyConverter.toBaseMinor(20_028, "KRW", "CNY", cny))
        assertEquals(89L, CurrencyConverter.betweenAccounts(100, "USD", "EUR", "CNY", cny))

        val usd = snapshot(parsed, "USD")
        assertFalse(usd.quotesPerUnit.containsKey("USD"))
        val cnyPerUsd = BigDecimal.ONE
            .divide(parsed.foreignPerOneCny.getValue("USD"), 16, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        assertEquals(0, cnyPerUsd.compareTo(usd.quotesPerUnit.getValue("CNY")))
        val eurPerUsd = parsed.foreignPerOneCny.getValue("EUR")
            .divide(parsed.foreignPerOneCny.getValue("USD"), 16, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        assertEquals(0, eurPerUsd.compareTo(usd.quotesPerUnit.getValue("EUR")))
        assertEquals(15L, CurrencyConverter.toBaseMinor(100, "CNY", "USD", usd))
    }

    private fun snapshot(parsed: CnyQuotedRates, base: String) = ExchangeRateSnapshot(
        baseCode = base,
        quotesPerUnit = CnyCrossRates.quotesPerUnit(parsed.foreignPerOneCny, base),
        fetchedAt = parsed.publishedAtMillis,
        source = parsed.source,
    )

    private fun publishedAt(): Long =
        LocalDate.of(2026, 10, 16).atTime(9, 15).atZone(CfetsCcpr.BEIJING).toInstant().toEpochMilli()

    private fun divided(numerator: String, denominator: String): BigDecimal =
        BigDecimal(numerator).divide(BigDecimal(denominator), 16, RoundingMode.HALF_UP).stripTrailingZeros()

    private companion object {
        val LATEST_FIXTURE = """
            {
              "data": {"lastDate": "2026-10-16 9:15"},
              "records": [
                {"vrtEName": "USD/CNY", "price": "6.7220"},
                {"vrtEName": "EUR/CNY", "price": "7.5228"},
                {"vrtEName": "100JPY/CNY", "price": "4.2439"},
                {"vrtEName": "NZD/CNY", "price": "3.7710"},
                {"vrtEName": "CNY/KRW", "price": "200.28"},
                {"vrtEName": "CNY/THB", "price": "---"}
              ]
            }
        """.trimIndent()

        val HISTORY_FIXTURE = """
            {
              "data": {"head": ["USD/CNY", "100JPY/CNY", "CNY/KRW"]},
              "records": [
                {"date": "2026-10-16", "values": ["6.7220", "4.2439", "200.28"]}
              ]
            }
        """.trimIndent()
    }
}
