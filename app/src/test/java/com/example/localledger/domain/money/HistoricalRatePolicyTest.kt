package com.example.localledger.domain.money

import com.example.localledger.domain.model.ExchangeRateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class HistoricalRatePolicyTest {
    private val early = snapshot(1_000L, "7")
    private val late = snapshot(9_000L, "5")

    @Test
    fun emptyHistorySelectsNothing() {
        assertNull(HistoricalRatePolicy.select(emptyList(), 2_000L, RevalueMode.AS_OF_TRANSACTION))
        assertNull(HistoricalRatePolicy.select(emptyList(), 2_000L, RevalueMode.CURRENT))
    }

    @Test
    fun asOfUsesTheLatestCacheNotAfterTheTransaction() {
        val selected = HistoricalRatePolicy.select(listOf(late, early), 2_000L, RevalueMode.AS_OF_TRANSACTION)
        assertEquals(early, selected)
    }

    @Test
    fun asOfFallsBackToTheCurrentCacheWhenEverySnapshotIsLater() {
        val selected = HistoricalRatePolicy.select(listOf(early, late), 100L, RevalueMode.AS_OF_TRANSACTION)
        assertEquals(late, selected)
    }

    @Test
    fun currentModeIgnoresTheTransactionDate() {
        val selected = HistoricalRatePolicy.select(listOf(early, late), 100L, RevalueMode.CURRENT)
        assertEquals(late, selected)
    }

    private fun snapshot(fetchedAt: Long, cnyPerUsd: String) = ExchangeRateSnapshot(
        baseCode = "USD",
        quotesPerUnit = mapOf("CNY" to BigDecimal(cnyPerUsd)),
        fetchedAt = fetchedAt,
        source = "test",
    )
}
