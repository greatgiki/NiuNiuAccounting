package com.example.localledger.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.domain.usecase.DeleteTransactionUseCase
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import com.example.localledger.domain.usecase.SaveTransferUseCase
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

sealed interface EditorEffect {
    data object NavigateBack : EditorEffect
    data class ShowMessage(val message: String) : EditorEffect
}

data class EditorInput(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val transactionAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val saving: Boolean = false,
    val initialized: Boolean = false,
    val amountError: String? = null,
    val categoryError: String? = null,
    val accountError: String? = null,
    val generalError: String? = null,
    val notFound: Boolean = false,
)

data class EditorUiState(
    val isEdit: Boolean,
    val isLoading: Boolean = true,
    val input: EditorInput = EditorInput(),
    val activeAccounts: List<Account> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val activeCategories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val canSave: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appSession: AppSession,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val saveIncomeExpenseUseCase: SaveIncomeExpenseUseCase,
    private val saveTransferUseCase: SaveTransferUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val transactionId: String? = savedStateHandle["transactionId"]
    private val zoneId: ZoneId = dateRangeResolver.zoneId()

    private val inputFlow = MutableStateFlow(
        EditorInput(
            type = savedStateHandle.get<String>("type")
                ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
                ?: TransactionType.EXPENSE,
        ),
    )

    private val effectChannel = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<EditorUiState> = combine(
        appSession.state,
        inputFlow,
    ) { session, input -> Pair(session, input) }
        .flatMapLatest { (session, input) ->
            val ledgerId = session.currentLedgerId
            if (ledgerId == null) {
                flowOf(EditorUiState(isEdit = transactionId != null, isLoading = false))
            } else {
                combine(
                    accountRepository.observeAccounts(ledgerId),
                    categoryRepository.observeCategories(ledgerId, categoryTypeOf(input.type)),
                ) { accounts, categories ->
                    buildState(ledgerId, session, input, accounts, categories)
                }
            }
        }
        .catch { error ->
            val current = uiState.value
            emit(
                current.copy(
                    isLoading = false,
                    input = current.input.copy(generalError = error.message ?: "读取本地数据失败"),
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorUiState(isEdit = transactionId != null))

    init {
        if (transactionId != null) {
            viewModelScope.launch {
                try {
                    val detail = transactionRepository.observeTransaction(transactionId).first()
                    if (detail == null) {
                        inputFlow.update { it.copy(notFound = true, initialized = true) }
                    } else if (!inputFlow.value.initialized) {
                        val t = detail.transaction
                        inputFlow.update {
                            it.copy(
                                type = t.type,
                                amountText = MoneyFormatter.minorToInput(t.amountMinor),
                                categoryId = t.categoryId,
                                accountId = t.accountId,
                                toAccountId = t.toAccountId,
                                transactionAt = t.transactionAt,
                                note = t.note.orEmpty(),
                                initialized = true,
                            )
                        }
                    }
                } catch (error: Exception) {
                    inputFlow.update {
                        it.copy(
                            generalError = error.message ?: "读取流水失败",
                            initialized = true,
                        )
                    }
                }
            }
        } else {
            inputFlow.update { it.copy(initialized = true) }
        }
    }

    private fun buildState(
        ledgerId: String,
        session: com.example.localledger.ui.SessionState,
        input: EditorInput,
        accounts: List<Account>,
        categories: List<Category>,
    ): EditorUiState {
        val activeAccounts = accounts.filter { !it.isArchived }
        val activeCategories = categories.filter { !it.isArchived }
        val lastAccountId = session.settings?.lastAccountIdByLedger?.get(ledgerId)
        val effectiveAccountId = input.accountId
            ?.takeIf { id -> accounts.any { it.id == id } }
            ?: lastAccountId?.takeIf { id -> activeAccounts.any { it.id == id } }
            ?: activeAccounts.firstOrNull()?.id
        val effectiveToAccountId = if (input.type == TransactionType.TRANSFER) {
            input.toAccountId
                ?.takeIf { id -> accounts.any { it.id == id } && id != effectiveAccountId }
                ?: activeAccounts.firstOrNull { it.id != effectiveAccountId }?.id
        } else {
            null
        }
        val normalized = input.copy(
            accountId = effectiveAccountId,
            toAccountId = effectiveToAccountId,
        )
        val canSave = !input.saving && input.initialized && when (input.type) {
            TransactionType.TRANSFER ->
                input.amountText.isNotBlank() &&
                    effectiveAccountId != null &&
                    effectiveToAccountId != null &&
                    effectiveAccountId != effectiveToAccountId
            else ->
                input.amountText.isNotBlank() &&
                    input.categoryId != null &&
                    effectiveAccountId != null
        }
        return EditorUiState(
            isEdit = transactionId != null,
            isLoading = !input.initialized,
            input = normalized,
            activeAccounts = activeAccounts,
            allAccounts = accounts,
            activeCategories = activeCategories,
            allCategories = categories,
            canSave = canSave,
        )
    }

    fun onTypeChange(type: TransactionType) {
        inputFlow.update {
            it.copy(
                type = type,
                categoryId = if (type == TransactionType.TRANSFER) null else it.categoryId,
                toAccountId = if (type == TransactionType.TRANSFER) it.toAccountId else null,
                categoryError = null,
                accountError = null,
            )
        }
    }

    fun onAmountKey(key: String) {
        inputFlow.update { input ->
            val current = input.amountText
            val next = when (key) {
                "backspace" -> current.dropLast(1)
                "clear" -> ""
                "." -> if (current.contains(".")) current else current.ifEmpty { "0" } + "."
                else -> {
                    val candidate = current + key
                    val dotIndex = candidate.indexOf('.')
                    val integerPart = if (dotIndex >= 0) candidate.substring(0, dotIndex) else candidate
                    val decimalPart = if (dotIndex >= 0) candidate.substring(dotIndex + 1) else ""
                    when {
                        integerPart.length > 13 -> current
                        decimalPart.length > 2 -> current
                        integerPart.length > 1 && integerPart.startsWith("0") && dotIndex < 0 ->
                            integerPart.trimStart('0').ifEmpty { "0" }
                        else -> candidate
                    }
                }
            }
            input.copy(amountText = next, amountError = null)
        }
    }

    fun onCategorySelect(categoryId: String) {
        inputFlow.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onAccountSelect(accountId: String) {
        inputFlow.update { it.copy(accountId = accountId, accountError = null) }
    }

    fun onToAccountSelect(accountId: String) {
        inputFlow.update { it.copy(toAccountId = accountId, accountError = null) }
    }

    fun swapTransferAccounts() {
        inputFlow.update { it.copy(accountId = it.toAccountId, toAccountId = it.accountId) }
    }

    fun onNoteChange(note: String) {
        inputFlow.update { it.copy(note = note.take(200)) }
    }

    fun onDateChange(date: LocalDate) {
        inputFlow.update {
            val time = Instant.ofEpochMilli(it.transactionAt).atZone(zoneId).toLocalTime()
            it.copy(transactionAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli())
        }
    }

    fun onTimeChange(time: LocalTime) {
        inputFlow.update {
            val date = Instant.ofEpochMilli(it.transactionAt).atZone(zoneId).toLocalDate()
            it.copy(transactionAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli())
        }
    }

    fun save() {
        val state = uiState.value
        val input = state.input
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        if (input.saving) return

        val amount = try {
            MoneyParser.yuanToMinor(input.amountText)
        } catch (error: LedgerException) {
            inputFlow.update { it.copy(amountError = error.message) }
            return
        }

        when (input.type) {
            TransactionType.TRANSFER -> {
                val to = input.toAccountId
                when {
                    input.accountId == null -> {
                        inputFlow.update { it.copy(accountError = "请选择转出账户") }
                        return
                    }
                    to == null -> {
                        inputFlow.update { it.copy(accountError = "请选择转入账户") }
                        return
                    }
                    input.accountId == to -> {
                        inputFlow.update { it.copy(accountError = "转出账户和转入账户不能相同") }
                        return
                    }
                }
            }
            else -> {
                if (input.categoryId == null) {
                    inputFlow.update { it.copy(categoryError = "请选择分类") }
                    return
                }
                if (input.accountId == null) {
                    inputFlow.update { it.copy(accountError = "请选择账户") }
                    return
                }
            }
        }

        inputFlow.update { it.copy(saving = true, generalError = null) }
        viewModelScope.launch {
            try {
                val command = SaveTransactionCommand(
                    transactionId = transactionId,
                    ledgerId = ledgerId,
                    type = input.type,
                    amountMinor = amount,
                    accountId = input.accountId!!,
                    toAccountId = if (input.type == TransactionType.TRANSFER) input.toAccountId else null,
                    categoryId = if (input.type == TransactionType.TRANSFER) null else input.categoryId,
                    transactionAt = input.transactionAt,
                    note = input.note,
                )
                if (input.type == TransactionType.TRANSFER) {
                    saveTransferUseCase(command)
                } else {
                    saveIncomeExpenseUseCase(command)
                }
                input.accountId?.let { appSession.recordLastAccount(ledgerId, it) }
                effectChannel.send(EditorEffect.NavigateBack)
            } catch (error: LedgerException) {
                inputFlow.update { it.copy(saving = false) }
                when (error) {
                    is LedgerException.SameTransferAccount,
                    is LedgerException.AccountArchived,
                    is LedgerException.AccountNotFound,
                    is LedgerException.AccountLedgerMismatch,
                    -> inputFlow.update { it.copy(accountError = error.message) }
                    is LedgerException.CategoryArchived,
                    is LedgerException.CategoryNotFound,
                    is LedgerException.CategoryTypeMismatch,
                    is LedgerException.CategoryLedgerMismatch,
                    -> inputFlow.update { it.copy(categoryError = error.message) }
                    is LedgerException.InvalidAmount ->
                        inputFlow.update { it.copy(amountError = error.message) }
                    else -> effectChannel.send(
                        EditorEffect.ShowMessage(error.message ?: "本地操作失败"),
                    )
                }
            } catch (error: Exception) {
                inputFlow.update { it.copy(saving = false) }
                effectChannel.send(EditorEffect.ShowMessage(error.message ?: "本地操作失败"))
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(ledgerId, id)
                effectChannel.send(EditorEffect.NavigateBack)
            } catch (error: LedgerException) {
                effectChannel.send(EditorEffect.ShowMessage(error.message ?: "删除失败"))
            }
        }
    }

    private fun categoryTypeOf(type: TransactionType): CategoryType = when (type) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }
}
