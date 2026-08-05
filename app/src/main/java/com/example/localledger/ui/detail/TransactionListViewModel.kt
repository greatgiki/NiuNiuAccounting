package com.example.localledger.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.domain.usecase.DeleteTransactionUseCase
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TransactionListFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    val isEmpty: Boolean
        get() = type == null && categoryId == null && accountId == null &&
            startDate == null && endDate == null
}

data class TransactionDayGroup(
    val date: LocalDate,
    val items: List<TransactionDetail>,
)

data class TransactionListUiState(
    val isLoading: Boolean = true,
    val groups: List<TransactionDayGroup> = emptyList(),
    val filter: TransactionListFilter = TransactionListFilter(),
    val keyword: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val error: String? = null,
)

sealed interface TransactionListEffect {
    data class ShowMessage(val message: String) : TransactionListEffect
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    appSession: AppSession,
    private val transactionRepository: TransactionRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val zoneId: ZoneId = dateRangeResolver.zoneId()

    private val filterFlow = MutableStateFlow(
        TransactionListFilter(
            type = savedStateHandle.get<String>("type")
                ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
            categoryId = savedStateHandle["categoryId"],
            accountId = savedStateHandle["accountId"],
            startDate = savedStateHandle.get<String>("startDate")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            endDate = savedStateHandle.get<String>("endDate")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        ),
    )
    private val keywordFlow = MutableStateFlow("")
    private val refreshFlow = MutableStateFlow(0)

    private val effectChannel = Channel<TransactionListEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val queryFlow = combine(
        appSession.state,
        filterFlow,
        keywordFlow.debounce(300),
        refreshFlow,
    ) { session, filter, keyword, _ ->
        Triple(session.currentLedgerId, filter, keyword)
    }

    private val transactionsFlow = queryFlow.flatMapLatest { (ledgerId, filter, keyword) ->
        if (ledgerId == null) {
            flowOf(Result.success(emptyList()))
        } else {
            val range = resolveRange(filter)
            transactionRepository.observeTransactions(
                ledgerId,
                TransactionFilter(
                    type = filter.type,
                    categoryId = filter.categoryId,
                    accountId = filter.accountId,
                    range = range,
                    keyword = keyword,
                ),
            ).map { Result.success(it) }
                .catch { error -> emit(Result.failure(error)) }
        }
    }

    val uiState: StateFlow<TransactionListUiState> = combine(
        appSession.state,
        filterFlow,
        keywordFlow,
        refreshFlow,
    ) { session, filter, keyword, _ -> Triple(session.currentLedgerId, filter, keyword) }
        .flatMapLatest { (ledgerId, filter, keyword) ->
            if (ledgerId == null) {
                flowOf(
                    TransactionListUiState(
                        isLoading = false,
                        filter = filter,
                        keyword = keyword,
                    ),
                )
            } else {
                combine(
                    transactionsFlow,
                    categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE),
                    categoryRepository.observeCategories(ledgerId, CategoryType.INCOME),
                    accountRepository.observeAccounts(ledgerId),
                ) { transactions, expenseCategories, incomeCategories, accounts ->
                    transactions.fold(
                        onSuccess = { list ->
                            TransactionListUiState(
                                isLoading = false,
                                groups = groupByDate(list),
                                filter = filter,
                                keyword = keyword,
                                categories = expenseCategories + incomeCategories,
                                accounts = accounts,
                            )
                        },
                        onFailure = { error ->
                            TransactionListUiState(
                                isLoading = false,
                                filter = filter,
                                keyword = keyword,
                                categories = expenseCategories + incomeCategories,
                                accounts = accounts,
                                error = error.message ?: "读取本地数据失败",
                            )
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListUiState())

    fun onKeywordChange(keyword: String) {
        keywordFlow.value = keyword.take(100)
    }

    fun updateFilter(transform: (TransactionListFilter) -> TransactionListFilter) {
        filterFlow.update(transform)
    }

    fun clearFilter() {
        filterFlow.value = TransactionListFilter()
        keywordFlow.value = ""
    }

    fun retry() {
        refreshFlow.value += 1
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(transactionId)
            } catch (error: LedgerException) {
                effectChannel.send(
                    TransactionListEffect.ShowMessage(error.message ?: "删除失败"),
                )
            }
        }
    }

    private fun resolveRange(filter: TransactionListFilter) =
        if (filter.startDate != null && filter.endDate != null) {
            try {
                dateRangeResolver.resolve(
                    StatisticsPeriod.Custom(filter.startDate, filter.endDate),
                )
            } catch (error: LedgerException) {
                null
            }
        } else {
            null
        }

    private fun groupByDate(list: List<TransactionDetail>): List<TransactionDayGroup> =
        list.groupBy { detail ->
            Instant.ofEpochMilli(detail.transaction.transactionAt).atZone(zoneId).toLocalDate()
        }.map { (date, items) -> TransactionDayGroup(date, items) }
}
