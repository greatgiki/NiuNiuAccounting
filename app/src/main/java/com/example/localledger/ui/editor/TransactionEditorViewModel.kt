package com.example.localledger.ui.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.data.repository.MerchantCategoryBook
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.importing.MerchantMemoryPolicy
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.data.ai.AiSettingsStore
import com.example.localledger.domain.ai.LedgerEntryAssistant
import com.example.localledger.domain.ai.UtteranceInterpretation
import com.example.localledger.domain.ai.VoiceParseConfidence
import com.example.localledger.domain.voice.VoiceAccountRef
import com.example.localledger.domain.voice.VoiceCategoryRef
import com.example.localledger.domain.voice.VoiceLedgerDraft
import com.example.localledger.domain.voice.VoiceLedgerParser
import com.example.localledger.domain.voice.VoiceMerchantOverride
import com.example.localledger.ui.voice.VoiceDraftSave
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
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
    private val merchantCategoryBook: MerchantCategoryBook,
    private val entryAssistant: LedgerEntryAssistant,
    aiSettingsStore: AiSettingsStore,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val savedState: SavedStateHandle = savedStateHandle
    private val transactionId: String? = savedState["transactionId"]
    internal val zoneId: ZoneId = dateRangeResolver.zoneId()
    internal val voiceExpenseCategories = catalog.expenseCategories
    internal val voiceIncomeCategories = catalog.incomeCategories
    internal val voiceAccounts = catalog.accounts
    private val voiceSaveGate = AtomicBoolean(false)
    private val initialType: TransactionType = savedState.get<String>("type")
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
    val aiAssistEnabled: StateFlow<Boolean> = aiSettingsStore.settings
        .map { it.enabled }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            aiSettingsStore.settings.value.enabled,
        )

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

    fun takeVoiceLaunch(): Boolean {
        if (transactionId != null) return false
        if (savedState.get<Boolean>(VOICE_LAUNCH_CONSUMED) == true) return false
        val requested = when (val raw = savedState.get<Any>("voice")) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            else -> false
        }
        if (!requested) return false
        savedState[VOICE_LAUNCH_CONSUMED] = true
        return true
    }

    suspend fun parseVoice(text: String): List<VoiceLedgerDraft> {
        val context = voiceContext()
        return VoiceLedgerParser.parse(
            text,
            context.categories,
            context.accounts,
            context.today,
            context.overrides,
        )
    }

    suspend fun localIsConfident(text: String): Boolean {
        val context = voiceContext()
        val local = VoiceLedgerParser.parse(
            text,
            context.categories,
            context.accounts,
            context.today,
            context.overrides,
        )
        return VoiceParseConfidence.isConfident(local)
    }

    suspend fun interpretUtterance(text: String, forceAi: Boolean): UtteranceInterpretation {
        val context = voiceContext()
        val local = VoiceLedgerParser.parse(
            text,
            context.categories,
            context.accounts,
            context.today,
            context.overrides,
        )
        return entryAssistant.interpret(
            utterance = text,
            local = local,
            categories = context.categories,
            accounts = context.accounts,
            today = context.today,
            force = forceAi,
            aiEnabled = aiAssistEnabled.value,
        )
    }

    private suspend fun voiceContext(): VoiceContext = withContext(Dispatchers.IO) {
        val categories = (voiceExpenseCategories.value.active + voiceIncomeCategories.value.active)
            .map { VoiceCategoryRef(it.id, it.name, it.type) }
        val accounts = voiceAccounts.value.active.map { VoiceAccountRef(it.id, it.name, it.type) }
        val ledgerId = appSession.state.value.currentLedgerId
        val overrides = if (ledgerId.isNullOrBlank()) {
            emptyList()
        } else {
            merchantOverrides(ledgerId, TransactionType.EXPENSE) +
                merchantOverrides(ledgerId, TransactionType.INCOME)
        }
        VoiceContext(categories, accounts, LocalDate.now(zoneId), overrides)
    }

    private data class VoiceContext(
        val categories: List<VoiceCategoryRef>,
        val accounts: List<VoiceAccountRef>,
        val today: LocalDate,
        val overrides: List<VoiceMerchantOverride>,
    )

    private suspend fun merchantOverrides(ledgerId: String, type: TransactionType): List<VoiceMerchantOverride> {
        return merchantCategoryBook.keys(ledgerId, type).map { (key, categoryId) ->
            VoiceMerchantOverride(merchantKey = key, categoryId = categoryId, type = type)
        }
    }

    suspend fun saveVoiceDrafts(drafts: List<VoiceDraftSave>): UiOutcome {
        if (!voiceSaveGate.compareAndSet(false, true)) return UiOutcome.Failure("正在保存")
        try {
            val ledgerId = appSession.state.value.currentLedgerId
                ?: return UiOutcome.Failure("请先选择账本")
            if (drafts.isEmpty()) return UiOutcome.Failure("没有可记的内容")
            val prepared = try {
                drafts.map { toVoiceCommand(ledgerId, it) }
            } catch (error: LedgerException) {
                return UiOutcome.Failure(error.message ?: "保存失败")
            }
            return try {
                prepared.forEach { command -> saveIncomeExpenseUseCase(command) }
                drafts.last().accountId.let { appSession.recordLastAccount(ledgerId, it) }
                effectChannel.send(EditorEffect.NavigateBack)
                UiOutcome.Success
            } catch (error: CancellationException) {
                throw error
            } catch (error: LedgerException) {
                UiOutcome.Failure(error.message ?: "保存失败")
            } catch (error: Exception) {
                UiOutcome.Failure(error.message ?: "保存失败")
            }
        } finally {
            voiceSaveGate.set(false)
        }
    }

    private fun toVoiceCommand(ledgerId: String, draft: VoiceDraftSave): SaveTransactionCommand {
        if (draft.type == TransactionType.TRANSFER) throw LedgerException.InvalidTransactionType()
        val amount = MoneyParser.toMinor(draft.amountText, scaleOf(draft.accountId))
        val categories = voiceExpenseCategories.value.all + voiceIncomeCategories.value.all
        val category = categories.firstOrNull { it.id == draft.categoryId } ?: throw LedgerException.CategoryNotFound()
        val expected = if (draft.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        if (category.type != expected) throw LedgerException.CategoryTypeMismatch()
        val account = voiceAccounts.value.all.firstOrNull { it.id == draft.accountId }
            ?: throw LedgerException.AccountNotFound()
        if (account.isArchived) throw LedgerException.AccountArchived()
        return SaveTransactionCommand(
            ledgerId = ledgerId,
            type = draft.type,
            amountMinor = amount,
            accountId = draft.accountId,
            categoryId = draft.categoryId,
            transactionAt = draft.date.atTime(draft.time).atZone(zoneId).toInstant().toEpochMilli(),
            note = draft.note.trim().ifBlank { null },
            counterparty = draft.merchant?.trim()?.take(40)?.ifBlank { null },
            rememberMerchantCategory = MerchantMemoryPolicy.shouldRemember(
                type = draft.type,
                merchant = draft.merchant,
                categoryId = draft.categoryId,
                previousCategoryId = draft.baselineCategoryId,
                categoryChanged = draft.categoryId != draft.baselineCategoryId,
            ),
        )
    }

    private fun scaleOf(accountId: String): Int {
        val code = voiceAccounts.value.all.firstOrNull { it.id == accountId }?.currencyCode ?: "CNY"
        return CurrencyCatalog.of(code).minorDigits
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

    private companion object {
        const val VOICE_LAUNCH_CONSUMED = "voiceLaunchConsumed"
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
