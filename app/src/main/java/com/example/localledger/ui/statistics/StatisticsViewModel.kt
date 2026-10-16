package com.example.localledger.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.budget.BudgetMath
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.MonthlyTotal
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.statistics.TrendFill
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.ui.home.HomeBudgetLine
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class TrendPoint(
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val period: StatisticsPeriod,
    val periodText: String = "",
    val currencyCode: String = "CNY",
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val incomeCategories: List<CategoryTotal> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val accountBalances: List<AccountBalance> = emptyList(),
    val budgets: List<HomeBudgetLine> = emptyList(),
    val error: String? = null,
)

private const val MAX_DAILY_TREND_DAYS = 62L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    appSession: AppSession,
    private val statisticsRepository: StatisticsRepository,
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
    private val dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val periodFlow = MutableStateFlow<StatisticsPeriod>(
        StatisticsPeriod.Month(YearMonth.now()),
    )
    private val refreshFlow = MutableStateFlow(0)

    val uiState: StateFlow<StatisticsUiState> = combine(
        appSession.state,
        periodFlow,
        refreshFlow,
    ) { session, period, _ ->
            Triple(session.currentLedgerId, session.currentLedgerCurrency, period)
        }
        .flatMapLatest { (ledgerId, currencyCode, period) ->
            if (ledgerId == null) {
                flowOf(StatisticsUiState(isLoading = false, period = period, currencyCode = currencyCode))
            } else {
                val range = dateRangeResolver.resolve(period)
                val trendFlow = when (period) {
                    is StatisticsPeriod.Day -> flowOf(emptyList())
                    is StatisticsPeriod.Year -> statisticsRepository.observeMonthlyTrend(
                        ledgerId,
                        period.year,
                    ).let { flow ->
                        kotlinx.coroutines.flow.flow {
                            flow.collect { months ->
                                emit(
                                    months.map { month ->
                                        TrendPoint(
                                            label = DateTimeFormatters.monthShort(month.yearMonth),
                                            startDate = month.yearMonth.atDay(1),
                                            endDate = month.yearMonth.atEndOfMonth(),
                                            incomeMinor = month.incomeMinor,
                                            expenseMinor = month.expenseMinor,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    else -> statisticsRepository.observeDailyTrend(ledgerId, range).let { flow ->
                        kotlinx.coroutines.flow.flow {
                            flow.collect { days -> emit(buildTrend(period, days)) }
                        }
                    }
                }
                val budgetFlow = if (period is StatisticsPeriod.Month) {
                    budgetRepository.observeMonth(ledgerId, period.yearMonth)
                } else {
                    flowOf(emptyList())
                }
                combine(
                    combine(
                        statisticsRepository.observeSummary(ledgerId, range),
                        statisticsRepository.observeExpenseCategories(ledgerId, range),
                        statisticsRepository.observeIncomeCategories(ledgerId, range),
                    ) { summary, categories, incomeCategories ->
                        Triple(summary, categories, incomeCategories)
                    },
                    trendFlow,
                    combine(
                        accountRepository.observeAccountBalances(ledgerId),
                        budgetFlow,
                    ) { balances, budgets -> balances to budgets },
                ) { (summary, categories, incomeCategories), trend, (balances, budgets) ->
                    val spentByCategory = categories.associate { it.categoryId to it.amountMinor }
                    StatisticsUiState(
                        isLoading = false,
                        period = period,
                        periodText = periodText(period),
                        currencyCode = currencyCode,
                        incomeMinor = summary.incomeMinor,
                        expenseMinor = summary.expenseMinor,
                        categories = categories,
                        incomeCategories = incomeCategories,
                        trend = trend,
                        accountBalances = balances.filter { !it.account.isArchived },
                        budgets = budgets.map { budget ->
                            val spent = if (budget.categoryId == null) {
                                summary.expenseMinor
                            } else {
                                spentByCategory[budget.categoryId] ?: 0L
                            }
                            val figures = BudgetMath.figures(budget.amountMinor, spent)
                            HomeBudgetLine(
                                id = budget.id,
                                label = budget.categoryName ?: "本月总预算",
                                limitMinor = figures.limitMinor,
                                spentMinor = figures.spentMinor,
                                remainingMinor = figures.remainingMinor,
                                percentLabel = figures.percentLabel,
                                overspent = figures.overspent,
                            )
                        },
                    )
                }
            }
        }
        .catch { error ->
            emit(
                StatisticsUiState(
                    isLoading = false,
                    period = periodFlow.value,
                    error = error.message ?: "读取统计数据失败",
                ),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatisticsUiState(period = StatisticsPeriod.Month(YearMonth.now())),
        )

    fun selectDay(date: LocalDate) {
        periodFlow.value = StatisticsPeriod.Day(date)
    }

    fun selectMonth(yearMonth: YearMonth) {
        periodFlow.value = StatisticsPeriod.Month(yearMonth)
    }

    fun selectYear(year: Int) {
        periodFlow.value = StatisticsPeriod.Year(year)
    }

    fun selectCustom(startDate: LocalDate, endDate: LocalDate) {
        if (!endDate.isBefore(startDate)) {
            periodFlow.value = StatisticsPeriod.Custom(startDate, endDate)
        }
    }

    fun select(period: StatisticsPeriod) {
        periodFlow.value = period
    }

    fun retry() {
        refreshFlow.value += 1
    }

    private fun buildTrend(
        period: StatisticsPeriod,
        days: List<DailyTotal>,
    ): List<TrendPoint> {
        val byDate = days.associateBy { it.date }
        return when (period) {
            is StatisticsPeriod.Month -> {
                val first = period.yearMonth.atDay(1)
                val last = period.yearMonth.atEndOfMonth()
                generateSequence(first) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(last) }
                    .map { date ->
                        val day = byDate[date]
                        TrendPoint(
                            label = DateTimeFormatters.dayShort(date),
                            startDate = date,
                            endDate = date,
                            incomeMinor = day?.incomeMinor ?: 0,
                            expenseMinor = day?.expenseMinor ?: 0,
                        )
                    }.toList()
            }
            is StatisticsPeriod.Custom -> {
                val totalDays = period.startDate.until(period.endDate).days.toLong() + 1
                if (totalDays <= MAX_DAILY_TREND_DAYS) {
                    generateSequence(period.startDate) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(period.endDate) }
                        .map { date ->
                            val day = byDate[date]
                            TrendPoint(
                                label = DateTimeFormatters.dayShort(date),
                                startDate = date,
                                endDate = date,
                                incomeMinor = day?.incomeMinor ?: 0,
                                expenseMinor = day?.expenseMinor ?: 0,
                            )
                        }.toList()
                } else {
                    val actual = days.groupBy { YearMonth.from(it.date) }.map { (yearMonth, monthDays) ->
                        MonthlyTotal(
                            yearMonth = yearMonth,
                            incomeMinor = monthDays.fold(0L) { acc, day -> Math.addExact(acc, day.incomeMinor) },
                            expenseMinor = monthDays.fold(0L) { acc, day -> Math.addExact(acc, day.expenseMinor) },
                        )
                    }
                    TrendFill.fillMonths(
                        YearMonth.from(period.startDate),
                        YearMonth.from(period.endDate),
                        actual,
                    ).map { month ->
                        TrendPoint(
                            label = DateTimeFormatters.monthShort(month.yearMonth),
                            startDate = maxOf(month.yearMonth.atDay(1), period.startDate),
                            endDate = minOf(month.yearMonth.atEndOfMonth(), period.endDate),
                            incomeMinor = month.incomeMinor,
                            expenseMinor = month.expenseMinor,
                        )
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun periodText(period: StatisticsPeriod): String = when (period) {
        is StatisticsPeriod.Day -> DateTimeFormatters.date(period.date)
        is StatisticsPeriod.Month -> DateTimeFormatters.yearMonth(period.yearMonth)
        is StatisticsPeriod.Year -> "${period.year}年"
        is StatisticsPeriod.Custom ->
            "${DateTimeFormatters.date(period.startDate)} 至 ${DateTimeFormatters.date(period.endDate)}"
    }
}
