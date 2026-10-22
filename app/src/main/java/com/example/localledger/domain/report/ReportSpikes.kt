package com.example.localledger.domain.report

object ReportSpikes {
    /** Ignore tiny jumps. 5,000 minor units is ¥50.00 for a two-decimal currency. */
    const val MIN_DELTA_MINOR = 5_000L

    fun dayIsSpike(dayExpense: Long, previousExpense: Long, previousDays: Int): Boolean {
        if (dayExpense <= 0L || previousExpense <= 0L || previousDays <= 0) return false
        val average = previousExpense / previousDays
        if (average <= 0L) return false
        val delta = try {
            Math.subtractExact(dayExpense, average)
        } catch (error: ArithmeticException) {
            return false
        }
        if (delta < MIN_DELTA_MINOR) return false
        return dayExpense / average >= 2L
    }

    fun categoryIsSpike(current: Long, previous: Long): Boolean {
        if (current <= 0L || previous <= 0L) return false
        val delta = try {
            Math.subtractExact(current, previous)
        } catch (error: ArithmeticException) {
            return false
        }
        if (delta < MIN_DELTA_MINOR) return false
        return current / previous >= 2L
    }
}
