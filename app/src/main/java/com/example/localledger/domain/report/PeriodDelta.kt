package com.example.localledger.domain.report

import com.example.localledger.domain.error.LedgerException
import java.math.BigDecimal
import java.math.RoundingMode

data class PeriodDelta(
    val currentMinor: Long,
    val previousMinor: Long,
) {
    /** Current minus previous. Negative means this period is lower. */
    val absoluteMinor: Long
        get() = try {
            Math.subtractExact(currentMinor, previousMinor)
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }

    /**
     * Signed percent change versus the previous amount, one decimal place.
     * Null when the previous amount is zero or negative, because a percent has no calm baseline.
     */
    val signedPercent: BigDecimal?
        get() {
            if (previousMinor <= 0L) return null
            return BigDecimal.valueOf(currentMinor)
                .subtract(BigDecimal.valueOf(previousMinor))
                .multiply(BigDecimal(100))
                .divide(BigDecimal.valueOf(previousMinor), 1, RoundingMode.HALF_UP)
        }

    fun magnitudePercentLabel(): String? {
        val signed = signedPercent ?: return null
        return percentText(signed.abs())
    }
}

fun percentText(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    return "$plain%"
}

fun magnitudeMinor(value: Long): Long = when {
    value == Long.MIN_VALUE -> throw LedgerException.ArithmeticOverflow()
    value < 0L -> -value
    else -> value
}
