package com.example.localledger.ui.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.importing.MerchantMemoryPolicy
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.domain.usecase.DeleteTransactionUseCase
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import com.example.localledger.domain.usecase.SaveTransferUseCase
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.common.runOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

@Immutable
data class EditorInput(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val toAmountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val transactionAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val counterparty: String = "",
    val baselineCategoryId: String? = null,
    val saving: Boolean = false,
    val initialized: Boolean = false,
    val amountError: String? = null,
    val categoryError: String? = null,
    val accountError: String? = null,
    val generalError: String? = null,
    val notFound: Boolean = false,
)

@Immutable
data class EditorUiState(
    val isEdit: Boolean,
    val isLoading: Boolean = true,
    val input: EditorInput = EditorInput(),
    val activeAccounts: List<Account> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val activeCategories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val categoryRows: List<List<Category>> = emptyList(),
    val categoriesLoaded: Boolean = false,
    val canSave: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appSession: AppSession,
    private val catalog: EditorCatalog,
    private val transactionRepository: TransactionRepository,
    private val saveIncomeExpenseUseCase: SaveIncomeExpenseUseCase,
    private val saveTransferUseCase: SaveTransferUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val transactionId: String? = savedStateHandle["transactionId"]
    private val zoneId: ZoneId = dateRangeResolver.zoneId()
    private val initialType: TransactionType = savedStateHandle.get<String>("type")
        ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        ?: TransactionType.EXPENSE

    private val inputFlow = MutableStateFlow(
        EditorInput(
            type = initialType,
            initialized = transactionId == null,
        ),
    )

    private val effectChannel = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val categoryFlow = inputFlow
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> catalog.categoriesFor(type) }

    val uiState: StateFlow<EditorUiState> = combine(
        appSession.state,
        inputFlow,
        catalog.accounts,
        categoryFlow,
    ) { session, input, accounts, categories ->
        assembleEditorUiState(
            isEdit = transactionId != null,
            ledgerId = session.currentLedgerId,
            lastAccountId = session.currentLedgerId?.let { id ->
                session.settings?.lastAccountIdByLedger?.get(id)
            },
            input = input,
            accounts = accounts,
            categories = categories,
        )
    }
        .flowOn(Dispatchers.Default)
        .catch { error ->
            emit(
                assembleEditorUiState(
                    isEdit = transactionId != null,
                    ledgerId = appSession.state.value.currentLedgerId,
                    lastAccountId = null,
                    input = inputFlow.value.copy(generalError = error.message ?: "读取本地数据失败"),
                    accounts = catalog.accounts.value,
                    categories = catalog.categoriesFor(inputFlow.value.type).value,
                ).copy(isLoading = false),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialUiState(),
        )

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
                                amountText = MoneyFormatter.minorToInput(t.amountMinor, detail.accountCurrencyCode),
                                toAmountText = t.toAmountMinor?.let {
                                    MoneyFormatter.minorToInput(it, detail.toAccountCurrencyCode ?: detail.accountCurrencyCode)
                                }.orEmpty(),
                                categoryId = t.categoryId,
                                accountId = t.accountId,
                                toAccountId = t.toAccountId,
                                transactionAt = t.transactionAt,
                                note = t.note.orEmpty(),
                                counterparty = t.counterparty.orEmpty(),
                                baselineCategoryId = t.categoryId,
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
        }
    }

    private fun initialUiState(): EditorUiState {
        val session = appSession.state.value
        return assembleEditorUiState(
            isEdit = transactionId != null,
            ledgerId = session.currentLedgerId,
            lastAccountId = session.currentLedgerId?.let { id ->
                session.settings?.lastAccountIdByLedger?.get(id)
            },
            input = inputFlow.value,
            accounts = catalog.accounts.value,
            categories = catalog.categoriesFor(inputFlow.value.type).value,
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
        val scale = selectedScale(inputFlow.value.accountId)
        inputFlow.update { input ->
            val current = input.amountText
            val next = when (key) {
                "backspace" -> current.dropLast(1)
                "clear" -> ""
                "." -> if (scale == 0 || current.contains(".")) current else current.ifEmpty { "0" } + "."
                else -> {
                    val candidate = current + key
                    val dotIndex = candidate.indexOf('.')
                    val integerPart = if (dotIndex >= 0) candidate.substring(0, dotIndex) else candidate
                    val decimalPart = if (dotIndex >= 0) candidate.substring(dotIndex + 1) else ""
                    when {
                        integerPart.length > 13 -> current
                        decimalPart.length > scale -> current
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

    fun onToAmountChange(text: String) {
        inputFlow.update { it.copy(toAmountText = text.take(16), amountError = null) }
    }

    fun swapTransferAccounts() {
        inputFlow.update { it.copy(accountId = it.toAccountId, toAccountId = it.accountId) }
    }

    fun onNoteChange(note: String) {
        inputFlow.update { it.copy(note = note.take(200)) }
    }

    fun onCounterpartyChange(value: String) {
        inputFlow.update { it.copy(counterparty = value.take(40)) }
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
            MoneyParser.toMinor(input.amountText, selectedScale(input.accountId))
        } catch (error: LedgerException) {
            inputFlow.update { it.copy(amountError = error.message) }
            return
        }
        val toAmount = if (input.type == TransactionType.TRANSFER && input.toAmountText.isNotBlank()) {
            try {
                MoneyParser.toMinor(input.toAmountText, selectedScale(input.toAccountId))
            } catch (error: LedgerException) {
                inputFlow.update { it.copy(amountError = error.message) }
                return
            }
        } else {
            null
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
                    toAmountMinor = toAmount,
                    counterparty = if (input.type == TransactionType.TRANSFER) null else input.counterparty,
                    rememberMerchantCategory = MerchantMemoryPolicy.shouldRemember(
                        type = input.type,
                        merchant = input.counterparty,
                        categoryId = input.categoryId,
                        previousCategoryId = input.baselineCategoryId,
                        categoryChanged = input.categoryId != input.baselineCategoryId,
                    ),
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

    suspend fun delete(): UiOutcome {
        val id = transactionId ?: return UiOutcome.Failure("记录不存在")
        val ledgerId = appSession.state.value.currentLedgerId
            ?: return UiOutcome.Failure("请先选择账本")
        return runOutcome("删除失败") {
            deleteTransactionUseCase(ledgerId, id)
            effectChannel.send(EditorEffect.NavigateBack)
        }
    }

    private fun selectedScale(accountId: String?): Int {
        val code = uiState.value.allAccounts.firstOrNull { it.id == accountId }?.currencyCode ?: "CNY"
        return CurrencyCatalog.of(code).minorDigits
    }
}

internal fun assembleEditorUiState(
    isEdit: Boolean,
    ledgerId: String?,
    lastAccountId: String?,
    input: EditorInput,
    accounts: AccountSnapshot,
    categories: CategorySnapshot,
): EditorUiState {
    val effectiveAccountId = input.accountId
        ?.takeIf { id -> accounts.all.any { it.id == id } }
        ?: lastAccountId?.takeIf { id -> ledgerId != null && accounts.active.any { it.id == id } }
        ?: accounts.active.firstOrNull()?.id
    val effectiveToAccountId = if (input.type == TransactionType.TRANSFER) {
        input.toAccountId
            ?.takeIf { id -> accounts.all.any { it.id == id } && id != effectiveAccountId }
            ?: accounts.active.firstOrNull { it.id != effectiveAccountId }?.id
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
        isEdit = isEdit,
        isLoading = !input.initialized,
        input = normalized,
        activeAccounts = accounts.active,
        allAccounts = accounts.all,
        activeCategories = categories.active,
        allCategories = categories.all,
        categoryRows = categories.rows,
        categoriesLoaded = categories.loaded,
        canSave = canSave,
    )
}
