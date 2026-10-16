package com.example.localledger.domain.budget

import com.example.localledger.domain.error.LedgerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BudgetMathTest {
    @Test
    fun remainingAndRatioStayExactForLargeAmounts() {
        val limit = 9_000_000_000_000_000L
        val spent = 3_000_000_000_000_000L
        val figures = BudgetMath.figures(limit, spent)
        assertEquals(6_000_000_000_000_000L, figures.remainingMinor)
        assertFalse(figures.overspent)
        assertEquals(BigDecimal("0.33333333"), figures.ratio)
        assertEquals("33.3%", figures.percentLabel)
    }

    @Test
    fun overspendIsNegativeRemaining() {
        val figures = BudgetMath.figures(1_000, 2_500)
        assertTrue(figures.overspent)
        assertEquals(-1_500L, figures.remainingMinor)
        assertEquals("250.0%", figures.percentLabel)
    }

    @Test
    fun zeroSpendIsZeroPercent() {
        val figures = BudgetMath.figures(800, 0)
        assertEquals(800L, figures.remainingMinor)
        assertEquals("0.0%", figures.percentLabel)
    }

    @Test(expected = LedgerException.InvalidBudget::class)
    fun nonPositiveLimitIsRejected() {
        BudgetMath.figures(0, 10)
    }
}
