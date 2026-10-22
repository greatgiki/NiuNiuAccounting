package com.example.localledger.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryFrequency
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.LargestExpense
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.report.PeriodReport
import com.example.localledger.domain.report.PeriodReportComposer
import com.example.localledger.domain.report.ReportFacts
import com.example.localledger.domain.report.ReportKind
import com.example.localledger.domain.report.ReportLock
import com.example.localledger.domain.report.initialAnchor
import com.example.localledger.domain.report.normalizeAnchor
import com.example.localledger.domain.report.periodBounds
import com.example.localledger.domain.report.periodLabel
import com.example.localledger.domain.report.previousPeriod
import com.example.localledger.domain.report.reportContentVisible
import com.example.localledger.domain.report.reportKindOrNull
import com.example.localledger.domain.report.shiftAnchor
import com.example.localledger.domain.report.statisticsPeriod
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.security.AppLockController
import com.example.localledger.security.AppLockState
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ReportUiState(
    val loading: Boolean = true,
    val locked: Boolean = false,
    val kind: ReportKind = ReportKind.WEEK,
    val anchor: LocalDate = LocalDate.of(1970, 1, 5),
    val periodLabel: String = "",
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val report: PeriodReport? = null,
    val error: String? = null,
    val emptyMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeriodReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statisticsRepository: StatisticsRepository,
    private val budgetRepository: BudgetRepository,
    private val dateRangeResolver: DateRangeResolver,
    appSession: AppSession,
    private val appLock: AppLockController,
) : ViewModel() {
    private val kindOrNull = reportKindOrNull(savedStateHandle.get<String>("kind"))
    private val kind = kindOrNull ?: ReportKind.WEEK
    private val zone = dateRangeResolver.zoneId()
    private val anchor = MutableStateFlow(
        initialAnchor(kind, savedStateHandle.get<String>("anchor"), LocalDate.now(zone)),
    )
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<ReportUiState> = combine(
        appSession.state,
        anchor,
        refresh,
        appLock.state,
    ) { session, day, _, lock ->
        Triple(session, day, lock.phase)
    }.flatMapLatest { (session, day, phase) ->
        if (kindOrNull == null) {
            flowOf(
                ReportUiState(
                    loading = false,
                    error = "无法打开这份报表",
                    kind = kind,
                    anchor = day,
                ),
            )
        } else {
            observe(session, day, phase)
        }
    }.catch { error ->
        emit(
            ReportUiState(
                loading = false,
                kind = kind,
                anchor = anchor.value,
                periodLabel = runCatching { periodLabel(kind, anchor.value) }.getOrDefault(""),
                error = error.message ?: "读取报表失败",
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReportUiState(kind = kind, anchor = anchor.value, periodLabel = runCatching { periodLabel(kind, anchor.value) }.getOrDefault("")),
    )

    fun previous() = move(-1)

    fun next() = move(1)

    fun jump(date: LocalDate) {
        if (kindOrNull == null) return
        anchor.value = normalizeAnchor(kind, date)
    }

    fun retry() {
        refresh.value += 1
    }

    fun shareBlockReason(): String? {
        if (!reportContentVisible(appLock.state.value.phase.asReportLock())) {
            return "请先解锁后再查看或分享报表"
        }
        if (uiState.value.report == null) return "报表还没有准备好"
        return null
    }

    private fun move(steps: Long) {
        anchor.value = shiftAnchor(kind, anchor.value, steps) ?: return
    }

    private fun observe(session: SessionState, day: LocalDate, phase: AppLockState.Phase): Flow<ReportUiState> {
        val shell = shell(day)
        if (!reportContentVisible(phase.asReportLock())) {
            return flowOf(shell.copy(locked = true, report = null))
        }
        if (!session.isReady) return flowOf(shell.copy(loading = true))
        val ledgerId = session.currentLedgerId ?: return flowOf(shell.copy(emptyMessage = "还没有可统计的账本"))
        val previous = previousPeriod(kind, day) ?: return flowOf(shell.copy(error = "无法计算上一周期"))
        val currentRange = dateRangeResolver.resolve(statisticsPeriod(kind, day))
        val previousRange = dateRangeResolver.resolve(StatisticsPeriod.Custom(previous.first, previous.second))
        val budgets = if (kind == ReportKind.MONTH) {
            budgetRepository.observeMonth(ledgerId, YearMonth.from(shell.anchor))
        } else {
            flowOf(emptyList())
        }
        return flow {
            emit(shell.copy(loading = true))
            emitAll(
                reportFlow(
                    ledgerId = ledgerId,
                    ledgerName = session.currentLedgerName,
                    currencyCode = session.currentLedgerCurrency,
                    shell = shell,
                    currentRange = currentRange,
                    previousRange = previousRange,
                    budgets = budgets,
                ),
            )
        }
    }

    private fun shell(day: LocalDate) = ReportUiState(
        loading = false,
        kind = kind,
        anchor = day,
        periodLabel = periodLabel(kind, day),
        canPrevious = shiftAnchor(kind, day, -1) != null,
        canNext = shiftAnchor(kind, day, 1) != null,
    )

    private fun reportFlow(
        ledgerId: String,
        ledgerName: String,
        currencyCode: String,
        shell: ReportUiState,
        currentRange: DateTimeRange,
        previousRange: DateTimeRange,
        budgets: Flow<List<Budget>>,
    ): Flow<ReportUiState> {
        val summaries = combine(
            statisticsRepository.observeSummary(ledgerId, currentRange),
            statisticsRepository.observeSummary(ledgerId, previousRange),
        ) { current, previous -> current to previous }
        val categories = combine(
            statisticsRepository.observeExpenseCategories(ledgerId, currentRange),
            statisticsRepository.observeIncomeCategories(ledgerId, currentRange),
            statisticsRepository.observeExpenseCategories(ledgerId, previousRange),
        ) { expense, income, previousExpense -> Triple(expense, income, previousExpense) }
        val extras = combine(
            statisticsRepository.observeDailyTrend(ledgerId, currentRange),
            statisticsRepository.observeLargestExpense(ledgerId, currentRange),
            statisticsRepository.observeExpenseFrequency(ledgerId, currentRange),
            budgets,
        ) { days, largest, counts, budgetLines ->
            ReportExtras(days, largest, counts, budgetLines)
        }
        return combine(summaries, categories, extras) { sums, cats, extra ->
            val (start, end) = periodBounds(kind, shell.anchor)
            shell.copy(
                loading = false,
                report = PeriodReportComposer.compose(
                    ReportFacts(
                        kind = kind,
                        ledgerName = ledgerName,
                        currencyCode = currencyCode,
                        start = start,
                        endInclusive = end,
                        incomeMinor = sums.first.incomeMinor,
                        expenseMinor = sums.first.expenseMinor,
                        previousIncomeMinor = sums.second.incomeMinor,
                        previousExpenseMinor = sums.second.expenseMinor,
                        expenseCategories = cats.first,
                        incomeCategories = cats.second,
                        previousExpenseCategories = cats.third,
                        days = extra.days,
                        largestExpense = extra.largest,
                        expenseCounts = extra.counts,
                        budgets = extra.budgets,
                    ),
                ),
            )
        }
    }
}

private data class ReportExtras(
    val days: List<DailyTotal>,
    val largest: LargestExpense?,
    val counts: List<CategoryFrequency>,
    val budgets: List<Budget>,
)

private fun AppLockState.Phase.asReportLock(): ReportLock = when (this) {
    AppLockState.Phase.Unknown -> ReportLock.Unknown
    AppLockState.Phase.Locked -> ReportLock.Locked
    AppLockState.Phase.Unlocked -> ReportLock.Unlocked
}
