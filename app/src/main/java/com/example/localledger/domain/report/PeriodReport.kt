package com.example.localledger.domain.report

import androidx.compose.runtime.Immutable
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryFrequency
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.LargestExpense
import java.time.LocalDate

enum class HighlightKind { LARGEST, FREQUENT, BUDGET, SPIKE }

@Immutable
data class ReportHighlight(
    val kind: HighlightKind,
    val title: String,
    val body: String,
)

@Immutable
data class WeekSlice(
    val label: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

@Immutable
data class PeriodReport(
    val kind: ReportKind,
    val periodLabel: String,
    val ledgerName: String,
    val currencyCode: String,
    val currencyLabel: String,
    val prose: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val balanceMinor: Long,
    val expenseComparison: String,
    val incomeComparison: String,
    val balanceComparison: String,
    val expenseCategories: List<CategoryTotal>,
    val incomeCategories: List<CategoryTotal>,
    val days: List<DailyTotal>,
    val weeks: List<WeekSlice>,
    val highlights: List<ReportHighlight>,
    val shareText: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

data class ReportFacts(
    val kind: ReportKind,
    val ledgerName: String,
    val currencyCode: String,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val previousIncomeMinor: Long,
    val previousExpenseMinor: Long,
    val expenseCategories: List<CategoryTotal> = emptyList(),
    val incomeCategories: List<CategoryTotal> = emptyList(),
    val previousExpenseCategories: List<CategoryTotal> = emptyList(),
    val days: List<DailyTotal> = emptyList(),
    val largestExpense: LargestExpense? = null,
    val expenseCounts: List<CategoryFrequency> = emptyList(),
    val budgets: List<Budget> = emptyList(),
)
