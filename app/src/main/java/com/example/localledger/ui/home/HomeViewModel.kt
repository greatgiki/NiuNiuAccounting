package com.example.localledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.repository.AccountRepository
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val ledgerName: String = "",
    val yearMonth: YearMonth,
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val accountBalances: List<AccountBalance> = emptyList(),
    val recentTransactions: List<TransactionDetail> = emptyList(),
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
        Triple(session.currentLedgerId, session.currentLedgerName, yearMonth)
    }.flatMapLatest { (ledgerId, ledgerName, yearMonth) ->
        if (ledgerId == null) {
            flowOf(HomeUiState(isLoading = false, yearMonth = yearMonth))
        } else {
            val range = dateRangeResolver.resolve(StatisticsPeriod.Month(yearMonth))
            combine(
                statisticsRepository.observeSummary(ledgerId, range),
                accountRepository.observeAccountBalances(ledgerId),
                transactionRepository.observeTransactions(ledgerId, TransactionFilter(limit = 10)),
            ) { summary, balances, recent ->
                HomeUiState(
                    isLoading = false,
                    ledgerName = ledgerName,
                    yearMonth = yearMonth,
                    incomeMinor = summary.incomeMinor,
                    expenseMinor = summary.expenseMinor,
                    accountBalances = balances.filter { !it.account.isArchived },
                    recentTransactions = recent,
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
