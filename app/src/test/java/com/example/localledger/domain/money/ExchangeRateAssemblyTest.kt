package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ExchangeRateAssemblyTest {
    @Test
    fun cfetsStaysPrimaryAndSupplementOnlyFillsMissingCurrencies() {
        val calls = mutableListOf<String>()
        val snapshot = assembly(
            calls,
            cfets = rates("USD" to "0.15", "JPY" to "20", source = CfetsCcpr.SOURCE),
            supplement = rates("USD" to "9", "TWD" to "4.8"),
            gap = { throw IllegalStateException("no more gaps") },
        ).fetch("CNY")

        assertEquals(listOf("cfets", "supplement", "gap"), calls)
        assertEquals(CfetsCcpr.SOURCE, snapshot.source.substringBefore("；"))
        assertTrue(snapshot.source.contains("currency-api（jsDelivr） 补齐未公布币种"))
        assertEquals(0, BigDecimal("0.15").compareTo(snapshot.quotesPerUnit.getValue("USD")))
        assertEquals(0, BigDecimal("4.8").compareTo(snapshot.quotesPerUnit.getValue("TWD")))
        assertEquals(1_600L, snapshot.fetchedAt)
    }

    @Test
    fun nonCnyBaseCrossesThroughTheCnyTable() {
        val snapshot = assembly(
            mutableListOf(),
            cfets = rates("USD" to "0.20", "EUR" to "0.10", source = CfetsCcpr.SOURCE),
            supplement = { error("unused") },
        ).fetch("USD")

        assertEquals(CfetsCcpr.SOURCE, snapshot.source)
        assertFalse(snapshot.quotesPerUnit.containsKey("USD"))
        assertEquals(0, BigDecimal("5").compareTo(snapshot.quotesPerUnit.getValue("CNY")))
        assertEquals(0, BigDecimal("0.5").compareTo(snapshot.quotesPerUnit.getValue("EUR")))
    }

    @Test
    fun gapFillerRunsOnlyAfterTheChinaSupplementFails() {
        val calls = mutableListOf<String>()
        val snapshot = assembly(
            calls,
            cfets = rates("USD" to "0.15", source = CfetsCcpr.SOURCE),
            supplement = { throw IllegalStateException("cdn down") },
            gap = rates("TWD" to "4.5", "USD" to "1", source = "open.er-api.com"),
        ).fetch("CNY")

        assertEquals(listOf("cfets", "supplement", "gap"), calls)
        assertEquals(0, BigDecimal("0.15").compareTo(snapshot.quotesPerUnit.getValue("USD")))
        assertEquals(0, BigDecimal("4.5").compareTo(snapshot.quotesPerUnit.getValue("TWD")))
        assertTrue(snapshot.source.contains("open.er-api.com 补齐未公布币种"))
    }

    @Test
    fun directSourcesAreLastAndDoNotReplaceEarlierQuotes() {
        val calls = mutableListOf<String>()
        val snapshot = assembly(
            calls,
            cfets = { throw IllegalStateException("cfets") },
            supplement = { throw IllegalStateException("cdn") },
            frank = snapshot("USD" to "0.14"),
            openEr = snapshot("USD" to "9", "TWD" to "4.2", source = "open.er-api.com"),
        ).fetch("CNY")

        assertEquals(listOf("cfets", "supplement", "gap", "frankfurter", "open-er"), calls)
        assertEquals("Frankfurter；open.er-api.com 补齐未公布币种", snapshot.source)
        assertEquals(0, BigDecimal("0.14").compareTo(snapshot.quotesPerUnit.getValue("USD")))
        assertEquals(0, BigDecimal("4.2").compareTo(snapshot.quotesPerUnit.getValue("TWD")))
    }

    @Test
    fun everySourceFailingKeepsTheErrorForTheCache() {
        val error = assertThrows(LedgerException.ExchangeRateUnavailable::class.java) {
            assembly(
                mutableListOf(),
                cfets = { throw IllegalStateException("cfets") },
                supplement = { throw IllegalStateException("cdn") },
                frank = { throw IllegalStateException("frank") },
                openEr = { throw IllegalStateException("er") },
            ).fetch("CNY")
        }
        assertEquals("没有可用汇率，请联网刷新后再记外币", error.message)
    }

    @Test
    fun cancellationIsNotTreatedAsASourceFailure() {
        val calls = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            assembly(
                calls,
                cfets = { throw CancellationException("stopped") },
                supplement = rates("USD" to "0.1"),
            ).fetch("CNY")
        }
        assertEquals(listOf("cfets"), calls)
    }

    private fun assembly(
        calls: MutableList<String>,
        cfets: () -> CnyQuotedRates,
        supplement: () -> CnyQuotedRates,
        gap: () -> CnyQuotedRates = { error("gap") },
        frank: () -> ExchangeRateSnapshot = { error("frank") },
        openEr: () -> ExchangeRateSnapshot = { error("open") },
    ): ExchangeRateAssembly = ExchangeRateAssembly(
        cfets = { calls += "cfets"; cfets() },
        supplement = { calls += "supplement"; supplement() },
        gapFillers = listOf({ calls += "gap"; gap() }),
        direct = listOf(
            { calls += "frankfurter"; frank() },
            { calls += "open-er"; openEr() },
        ),
    )

    private fun rates(
        vararg pairs: Pair<String, String>,
        source: String = "currency-api（jsDelivr）",
    ): () -> CnyQuotedRates = {
        CnyQuotedRates(
            foreignPerOneCny = pairs.toMap().mapValues { BigDecimal(it.value) },
            publishedAtMillis = 1_600L,
            source = source,
        )
    }

    private fun snapshot(
        vararg pairs: Pair<String, String>,
        source: String = "Frankfurter",
    ): () -> ExchangeRateSnapshot = {
        ExchangeRateSnapshot(
            baseCode = "CNY",
            quotesPerUnit = pairs.toMap().mapValues { BigDecimal(it.value) },
            fetchedAt = 2_000L,
            source = source,
        )
    }
}
