package com.example.localledger.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.budget.BudgetMath
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.domain.usecase.DeleteBudgetUseCase
import com.example.localledger.domain.usecase.SaveBudgetUseCase
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.home.HomeBudgetLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class BudgetManageUiState(
    val isLoading: Boolean = true,
    val yearMonth: YearMonth = YearMonth.now(),
    val currencyCode: String = "CNY",
    val lines: List<HomeBudgetLine> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: String? = null,
)

sealed interface BudgetEffect {
    data class ShowMessage(val message: String) : BudgetEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetManageViewModel @Inject constructor(
    private val appSession: AppSession,
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    statisticsRepository: StatisticsRepository,
    private val saveBudget: SaveBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
    private val dateRangeResolver: DateRangeResolver,
    private val clock: Clock,
) : ViewModel() {
    private val yearMonthFlow = MutableStateFlow(YearMonth.now(clock.withZone(dateRangeResolver.zoneId())))
    private val refreshFlow = MutableStateFlow(0)
    private val effectsChannel = Channel<BudgetEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    val uiState: StateFlow<BudgetManageUiState> = combine(appSession.state, yearMonthFlow, refreshFlow) { session, month, _ ->
        Triple(session.currentLedgerId, session.currentLedgerCurrency, month)
    }.flatMapLatest { (ledgerId, currency, month) ->
        if (ledgerId == null) {
            flowOf(BudgetManageUiState(isLoading = false, yearMonth = month, currencyCode = currency))
        } else {
            val range = dateRangeResolver.resolve(StatisticsPeriod.Month(month))
            combine(
                budgetRepository.observeMonth(ledgerId, month),
                categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE),
                statisticsRepository.observeSummary(ledgerId, range),
                statisticsRepository.observeExpenseCategories(ledgerId, range),
            ) { budgets, categories, summary, expenseCategories ->
                val spentByCategory = expenseCategories.associate { it.categoryId to it.amountMinor }
                BudgetManageUiState(
                    isLoading = false,
                    yearMonth = month,
                    currencyCode = currency,
                    budgets = budgets,
                    categories = categories.filter { !it.isArchived },
                    lines = budgets.map { budget ->
                        val spent = if (budget.categoryId == null) summary.expenseMinor else spentByCategory[budget.categoryId] ?: 0L
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
        emit(BudgetManageUiState(isLoading = false, yearMonth = yearMonthFlow.value, error = error.message ?: "读取预算失败"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetManageUiState())

    fun previousMonth() {
        yearMonthFlow.value = yearMonthFlow.value.minusMonths(1)
    }

    fun nextMonth() {
        yearMonthFlow.value = yearMonthFlow.value.plusMonths(1)
    }

    fun save(existing: Budget?, categoryId: String?, amountMinor: Long) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        if (amountMinor <= 0L) {
            viewModelScope.launch { effectsChannel.send(BudgetEffect.ShowMessage("预算金额必须大于 0")) }
            return
        }
        val now = clock.millis()
        val budget = Budget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            ledgerId = ledgerId,
            yearMonth = yearMonthFlow.value,
            categoryId = categoryId,
            amountMinor = amountMinor,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            try {
                saveBudget(budget)
            } catch (error: LedgerException) {
                effectsChannel.send(BudgetEffect.ShowMessage(error.message ?: "保存预算失败"))
            }
        }
    }

    fun delete(budgetId: String) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        viewModelScope.launch {
            try {
                deleteBudget(ledgerId, budgetId)
            } catch (error: LedgerException) {
                effectsChannel.send(BudgetEffect.ShowMessage(error.message ?: "删除预算失败"))
            }
        }
    }

    fun retry() {
        refreshFlow.value += 1
    }
}
