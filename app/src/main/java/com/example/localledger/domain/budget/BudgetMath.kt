package com.example.localledger.domain.budget

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.money.RatioMath
import java.math.BigDecimal

data class BudgetFigures(
    val limitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val overspent: Boolean,
    val ratio: BigDecimal,
    val percentLabel: String,
)

object BudgetMath {
    fun figures(limitMinor: Long, spentMinor: Long): BudgetFigures {
        if (limitMinor <= 0L) throw LedgerException.InvalidBudget()
        val remaining = try {
            Math.subtractExact(limitMinor, spentMinor)
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }
        return BudgetFigures(
            limitMinor = limitMinor,
            spentMinor = spentMinor,
            remainingMinor = remaining,
            overspent = spentMinor > limitMinor,
            ratio = RatioMath.ratio(spentMinor, limitMinor),
            percentLabel = RatioMath.percentLabel(spentMinor, limitMinor),
        )
    }
}
