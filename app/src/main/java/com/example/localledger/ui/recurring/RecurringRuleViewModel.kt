package com.example.localledger.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.ResumeChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.RecurringRuleRepository
import com.example.localledger.domain.time.ZoneIdProvider
import com.example.localledger.domain.usecase.DeleteRecurringRuleUseCase
import com.example.localledger.domain.usecase.ResumeRecurringRuleUseCase
import com.example.localledger.domain.usecase.SaveRecurringRuleUseCase
import com.example.localledger.domain.usecase.SetRecurringRulePausedUseCase
import com.example.localledger.ui.AppSession
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class RecurringUiState(
    val isLoading: Boolean = true,
    val currencyCode: String = "CNY",
    val rules: List<RecurringRule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val error: String? = null,
)

sealed interface RecurringEffect {
    data class ShowMessage(val message: String) : RecurringEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecurringRuleViewModel @Inject constructor(
    private val appSession: AppSession,
    recurringRuleRepository: RecurringRuleRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val saveRule: SaveRecurringRuleUseCase,
    private val deleteRule: DeleteRecurringRuleUseCase,
    private val setPaused: SetRecurringRulePausedUseCase,
    private val resumeRule: ResumeRecurringRuleUseCase,
    private val zoneIdProvider: ZoneIdProvider,
    private val clock: Clock,
) : ViewModel() {
    private val refreshFlow = MutableStateFlow(0)
    private val effectsChannel = Channel<RecurringEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    val uiState: StateFlow<RecurringUiState> = combine(appSession.state, refreshFlow) { session, _ ->
        session.currentLedgerId to session.currentLedgerCurrency
    }.flatMapLatest { (ledgerId, currency) ->
        if (ledgerId == null) {
            flowOf(RecurringUiState(isLoading = false, currencyCode = currency))
        } else {
            combine(
                recurringRuleRepository.observe(ledgerId),
                accountRepository.observeAccounts(ledgerId),
                categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE),
                categoryRepository.observeCategories(ledgerId, CategoryType.INCOME),
            ) { rules, accounts, expenses, incomes ->
                RecurringUiState(
                    isLoading = false,
                    currencyCode = currency,
                    rules = rules,
                    accounts = accounts.filter { !it.isArchived },
                    expenseCategories = expenses.filter { !it.isArchived },
                    incomeCategories = incomes.filter { !it.isArchived },
                )
            }
        }
    }.catch { error ->
        emit(RecurringUiState(isLoading = false, error = error.message ?: "读取周期规则失败"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun save(draft: RecurringDraft) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        val account = uiState.value.accounts.firstOrNull { it.id == draft.accountId }
        val digits = CurrencyCatalog.of(account?.currencyCode ?: "CNY").minorDigits
        val amount = try {
            MoneyParser.toMinor(draft.amountText, digits)
        } catch (error: LedgerException) {
            viewModelScope.launch { effectsChannel.send(RecurringEffect.ShowMessage(error.message ?: "金额不正确")) }
            return
        }
        val toAmount = draft.toAmountText.trim().takeIf { it.isNotEmpty() }?.let { text ->
            val toAccount = uiState.value.accounts.firstOrNull { it.id == draft.toAccountId }
            val toDigits = CurrencyCatalog.of(toAccount?.currencyCode ?: "CNY").minorDigits
            try {
                MoneyParser.toMinor(text, toDigits)
            } catch (error: LedgerException) {
                viewModelScope.launch { effectsChannel.send(RecurringEffect.ShowMessage("转入金额不正确")) }
                return
            }
        }
        val start = draft.startDate
        val end = draft.endDate
        if (end != null && end.isBefore(start)) {
            viewModelScope.launch { effectsChannel.send(RecurringEffect.ShowMessage("结束日期不能早于开始日期")) }
            return
        }
        val existing = draft.existing
        val today = LocalDate.now(clock.withZone(zoneIdProvider.current()))
        val next = existing?.nextRunDate?.takeIf { !it.isBefore(start) } ?: start
        val rule = RecurringRule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            ledgerId = ledgerId,
            type = draft.type,
            amountMinor = amount,
            toAmountMinor = if (draft.type == TransactionType.TRANSFER) toAmount else null,
            accountId = draft.accountId ?: return,
            toAccountId = if (draft.type == TransactionType.TRANSFER) draft.toAccountId else null,
            categoryId = if (draft.type == TransactionType.TRANSFER) null else draft.categoryId,
            note = draft.note.trim().ifEmpty { null },
            period = draft.period,
            startDate = start,
            endDate = end,
            nextRunDate = if (next.isBefore(today) && existing == null) start else next,
            paused = existing?.paused ?: false,
            createdAt = existing?.createdAt ?: clock.millis(),
            updatedAt = clock.millis(),
        )
        viewModelScope.launch {
            try {
                saveRule(rule)
            } catch (error: LedgerException) {
                effectsChannel.send(RecurringEffect.ShowMessage(error.message ?: "保存规则失败"))
            }
        }
    }

    fun pause(rule: RecurringRule) {
        viewModelScope.launch {
            try {
                setPaused(rule.ledgerId, rule.id, true)
            } catch (error: LedgerException) {
                effectsChannel.send(RecurringEffect.ShowMessage(error.message ?: "更新失败"))
            }
        }
    }

    fun resume(rule: RecurringRule, choice: ResumeChoice) {
        viewModelScope.launch {
            try {
                val created = resumeRule(rule.ledgerId, rule.id, choice)
                val message = when (choice) {
                    ResumeChoice.BACKFILL -> if (created > 0) "已补记 $created 笔" else "没有需要补记的日期"
                    ResumeChoice.SKIP_MISSED -> "已跳到下一次，不补记错过的日期"
                }
                effectsChannel.send(RecurringEffect.ShowMessage(message))
            } catch (error: LedgerException) {
                effectsChannel.send(RecurringEffect.ShowMessage(error.message ?: "更新失败"))
            }
        }
    }

    fun delete(rule: RecurringRule, deleteGenerated: Boolean) {
        viewModelScope.launch {
            try {
                deleteRule(rule.ledgerId, rule.id, deleteGenerated)
            } catch (error: LedgerException) {
                effectsChannel.send(RecurringEffect.ShowMessage(error.message ?: "删除失败"))
            }
        }
    }

    fun retry() {
        refreshFlow.value += 1
    }
}

data class RecurringDraft(
    val existing: RecurringRule?,
    val type: TransactionType,
    val amountText: String,
    val toAmountText: String,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val note: String,
    val period: RecurrencePeriod,
    val startDate: LocalDate,
    val endDate: LocalDate?,
)
