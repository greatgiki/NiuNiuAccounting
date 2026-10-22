package com.example.localledger.domain.calendar

import androidx.compose.runtime.Immutable
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Immutable
data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class DatedAmount(
    val transactionAt: Long,
    val type: TransactionType,
    val baseAmountMinor: Long,
)

object CalendarGrid {
    const val CELLS = 42

    fun cells(month: YearMonth, totals: Map<LocalDate, IncomeExpenseSummary>): List<CalendarCell> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value + 6) % 7
        val start = first.minusDays(lead.toLong())
        return List(CELLS) { offset ->
            val date = start.plusDays(offset.toLong())
            val total = totals[date]
            CalendarCell(
                date = date,
                inMonth = date.year == month.year && date.month == month.month,
                incomeMinor = total?.incomeMinor ?: 0L,
                expenseMinor = total?.expenseMinor ?: 0L,
            )
        }
    }

    fun bucket(rows: List<DatedAmount>, zone: ZoneId): Map<LocalDate, IncomeExpenseSummary> {
        if (rows.isEmpty()) return emptyMap()
        val income = HashMap<LocalDate, Long>()
        val expense = HashMap<LocalDate, Long>()
        for (row in rows) {
            val date = Instant.ofEpochMilli(row.transactionAt).atZone(zone).toLocalDate()
            when (row.type) {
                TransactionType.INCOME -> income[date] = (income[date] ?: 0L) + row.baseAmountMinor
                TransactionType.EXPENSE -> expense[date] = (expense[date] ?: 0L) + row.baseAmountMinor
                TransactionType.TRANSFER -> Unit
            }
        }
        val dates = income.keys + expense.keys
        return dates.associateWith { date ->
            IncomeExpenseSummary(
                incomeMinor = income[date] ?: 0L,
                expenseMinor = expense[date] ?: 0L,
            )
        }
    }

    /** Short yuan figure for a 1/7-width day cell. Full amounts stay on the day header. */
    fun compactYuan(minor: Long): String {
        if (minor == 0L) return ""
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val yuan = abs / 100
        return when {
            yuan >= 100_000_000 -> sign + (yuan / 100_000_000) + "亿"
            yuan >= 10_000 -> sign + (yuan / 10_000) + "万"
            yuan >= 1 -> sign + yuan.toString()
            else -> sign + "0." + (abs / 10).coerceAtLeast(1)
        }
    }
}

object CalendarPager {
    val origin: YearMonth = YearMonth.of(2016, 1)

    fun pageCount(today: YearMonth): Int {
        val end = today.plusYears(3)
        return ChronoUnit.MONTHS.between(origin, end).toInt() + 1
    }

    fun monthOf(page: Int): YearMonth = origin.plusMonths(page.toLong())

    fun pageOf(month: YearMonth, today: YearMonth): Int {
        val last = pageCount(today) - 1
        return ChronoUnit.MONTHS.between(origin, month).toInt().coerceIn(0, last)
    }
}
