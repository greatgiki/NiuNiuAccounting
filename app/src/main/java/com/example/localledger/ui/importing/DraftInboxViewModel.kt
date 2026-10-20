package com.example.localledger.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.repository.DraftConfirmResult
import com.example.localledger.data.repository.DraftRepository
import com.example.localledger.data.repository.PendingDraft
import com.example.localledger.domain.importing.BillAmounts
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class DraftInboxUiState(
    val drafts: List<PendingDraft> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val ready: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DraftInboxViewModel @Inject constructor(
    private val appSession: AppSession,
    private val draftRepository: DraftRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    val zoneId: ZoneId = dateRangeResolver.zoneId()
    val effects = MutableSharedFlow<DraftInboxEffect>(extraBufferCapacity = 1)

    val uiState: StateFlow<DraftInboxUiState> = appSession.state
        .map { it.currentLedgerId }
        .flatMapLatest { ledgerId ->
            if (ledgerId == null) {
                flowOf(DraftInboxUiState(ready = true))
            } else {
                kotlinx.coroutines.flow.combine(
                    draftRepository.observePending(ledgerId),
                    accountRepository.observeAccounts(ledgerId),
                    categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE),
                    categoryRepository.observeCategories(ledgerId, CategoryType.INCOME),
                ) { drafts, accounts, expenses, incomes ->
                    DraftInboxUiState(
                        drafts = drafts,
                        accounts = accounts.filter { !it.isArchived },
                        expenseCategories = expenses.filter { !it.isArchived },
                        incomeCategories = incomes.filter { !it.isArchived },
                        ready = true,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftInboxUiState())

    fun assign(draftId: String, categoryId: String?, accountId: String?) {
        viewModelScope.launch {
            draftRepository.updateAssignment(draftId, categoryId, accountId)
        }
    }

    fun confirm(draft: PendingDraft) {
        viewModelScope.launch {
            when (draftRepository.confirm(draft.id)) {
                is DraftConfirmResult.Confirmed -> effects.emit(DraftInboxEffect.Message("已记入流水"))
                DraftConfirmResult.MissingAssignment -> effects.emit(DraftInboxEffect.Message("请先选择分类和账户"))
                DraftConfirmResult.NeedsAmount -> effects.emit(DraftInboxEffect.Message("请先补全金额"))
                DraftConfirmResult.NotPending -> effects.emit(DraftInboxEffect.Message("这条草稿已经处理过"))
            }
        }
    }

    fun setAmount(draftId: String, raw: String) {
        viewModelScope.launch {
            val minor = BillAmounts.parseYuanToMinor(raw)
            if (minor == null) {
                effects.emit(DraftInboxEffect.Message("请输入有效金额"))
                return@launch
            }
            if (draftRepository.updateAmount(draftId, minor)) {
                effects.emit(DraftInboxEffect.Message("金额已补全"))
            }
        }
    }

    fun dismiss(draftId: String) {
        viewModelScope.launch {
            draftRepository.dismiss(draftId)
            effects.emit(DraftInboxEffect.Dismissed(draftId, "已忽略这条草稿"))
        }
    }

    fun restore(draftId: String) {
        viewModelScope.launch {
            if (draftRepository.restore(draftId)) {
                effects.emit(DraftInboxEffect.Message("已撤销忽略"))
            }
        }
    }
}

sealed interface DraftInboxEffect {
    data class Message(val text: String) : DraftInboxEffect
    data class Dismissed(val draftId: String, val text: String) : DraftInboxEffect
}

data class PendingDraftBadge(
    val count: Int = 0,
    val needsAmount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PendingDraftBadgeViewModel @Inject constructor(
    appSession: AppSession,
    draftRepository: DraftRepository,
) : ViewModel() {
    val badge: StateFlow<PendingDraftBadge> = appSession.state
        .map { it.currentLedgerId }
        .flatMapLatest { ledgerId ->
            if (ledgerId == null) {
                flowOf(PendingDraftBadge())
            } else {
                draftRepository.observePending(ledgerId).map { drafts ->
                    PendingDraftBadge(
                        count = drafts.size,
                        needsAmount = drafts.count { it.needsAmount },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingDraftBadge())
}

fun draftCategories(draft: PendingDraft, state: DraftInboxUiState): List<Category> {
    return if (draft.type == TransactionType.INCOME) state.incomeCategories else state.expenseCategories
}
