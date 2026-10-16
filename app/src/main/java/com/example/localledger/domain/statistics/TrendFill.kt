package com.example.localledger.domain.statistics

import com.example.localledger.domain.model.MonthlyTotal
import java.time.YearMonth

object TrendFill {
    fun fillMonths(
        start: YearMonth,
        endInclusive: YearMonth,
        actual: List<MonthlyTotal>,
    ): List<MonthlyTotal> {
        if (endInclusive.isBefore(start)) return emptyList()
        val byMonth = actual.associateBy { it.yearMonth }
        val result = ArrayList<MonthlyTotal>()
        var cursor = start
        while (!cursor.isAfter(endInclusive)) {
            result += byMonth[cursor] ?: MonthlyTotal(cursor, incomeMinor = 0, expenseMinor = 0)
            cursor = cursor.plusMonths(1)
        }
        return result
    }
}
