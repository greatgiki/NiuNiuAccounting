package com.example.localledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.budget.BudgetMath
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.ui.AppSession
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
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HomeBudgetLine(
    val id: String,
    val label: String,
    val limitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val percentLabel: String,
    val overspent: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val ledgerName: String = "",
    val currencyCode: String = "CNY",
    val yearMonth: YearMonth,
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val accountBalances: List<AccountBalance> = emptyList(),
    val recentTransactions: List<TransactionDetail> = emptyList(),
    val budgets: List<HomeBudgetLine> = emptyList(),
    val error: String? = null,
) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    appSession: AppSession,
    statisticsRepository: StatisticsRepository,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val yearMonthFlow = MutableStateFlow(YearMonth.now())
    private val refreshFlow = MutableStateFlow(0)
    val zoneId: ZoneId = dateRangeResolver.zoneId()

    val uiState: StateFlow<HomeUiState> = combine(
        appSession.state,
        yearMonthFlow,
        refreshFlow,
    ) { session, yearMonth, _ ->
        HomeQuery(session.currentLedgerId, session.currentLedgerName, session.currentLedgerCurrency, yearMonth)
    }.flatMapLatest { query ->
        val ledgerId = query.ledgerId
        if (ledgerId == null) {
            flowOf(HomeUiState(isLoading = false, yearMonth = query.yearMonth, currencyCode = query.currencyCode))
        } else {
            val range = dateRangeResolver.resolve(StatisticsPeriod.Month(query.yearMonth))
            combine(
                statisticsRepository.observeSummary(ledgerId, range),
                accountRepository.observeAccountBalances(ledgerId),
                transactionRepository.observeTransactions(ledgerId, TransactionFilter(limit = 10)),
                budgetRepository.observeMonth(ledgerId, query.yearMonth),
                statisticsRepository.observeExpenseCategories(ledgerId, range),
            ) { summary, balances, recent, budgets, categories ->
                val spentByCategory = categories.associate { it.categoryId to it.amountMinor }
                HomeUiState(
                    isLoading = false,
                    ledgerName = query.ledgerName,
                    currencyCode = query.currencyCode,
                    yearMonth = query.yearMonth,
                    incomeMinor = summary.incomeMinor,
                    expenseMinor = summary.expenseMinor,
                    accountBalances = balances.filter { !it.account.isArchived },
                    recentTransactions = recent,
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
    }.catch { error ->
        emit(
            HomeUiState(
                isLoading = false,
                yearMonth = yearMonthFlow.value,
                error = error.message ?: "读取本地数据失败",
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(yearMonth = YearMonth.now()),
    )

    fun previousMonth() {
        yearMonthFlow.value = yearMonthFlow.value.minusMonths(1)
    }

    fun nextMonth() {
        yearMonthFlow.value = yearMonthFlow.value.plusMonths(1)
    }

    fun retry() {
        refreshFlow.value += 1
    }
}

private data class HomeQuery(
    val ledgerId: String?,
    val ledgerName: String,
    val currencyCode: String,
    val yearMonth: YearMonth,
)
