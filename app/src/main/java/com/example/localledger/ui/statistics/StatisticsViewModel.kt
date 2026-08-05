package com.example.localledger.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.time.DateRangeResolver
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
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val accountBalances: List<AccountBalance> = emptyList(),
    val error: String? = null,
)

private const val MAX_DAILY_TREND_DAYS = 62L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    appSession: AppSession,
    private val statisticsRepository: StatisticsRepository,
    accountRepository: AccountRepository,
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
    ) { session, period, _ -> Pair(session.currentLedgerId, period) }
        .flatMapLatest { (ledgerId, period) ->
            if (ledgerId == null) {
                flowOf(StatisticsUiState(isLoading = false, period = period))
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
                combine(
                    statisticsRepository.observeSummary(ledgerId, range),
                    statisticsRepository.observeExpenseCategories(ledgerId, range),
                    trendFlow,
                    accountRepository.observeAccountBalances(ledgerId),
                ) { summary, categories, trend, balances ->
                    StatisticsUiState(
                        isLoading = false,
                        period = period,
                        periodText = periodText(period),
                        incomeMinor = summary.incomeMinor,
                        expenseMinor = summary.expenseMinor,
                        categories = categories,
                        trend = trend,
                        accountBalances = balances.filter { !it.account.isArchived },
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
                    days.groupBy { YearMonth.from(it.date) }
                        .toSortedMap()
                        .map { (yearMonth, monthDays) ->
                            TrendPoint(
                                label = DateTimeFormatters.monthShort(yearMonth),
                                startDate = maxOf(yearMonth.atDay(1), period.startDate),
                                endDate = minOf(yearMonth.atEndOfMonth(), period.endDate),
                                incomeMinor = monthDays.sumOf { it.incomeMinor },
                                expenseMinor = monthDays.sumOf { it.expenseMinor },
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
