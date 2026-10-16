package com.example.localledger.ui.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.repository.BillImportRepository
import com.example.localledger.data.repository.CommitImportRow
import com.example.localledger.data.repository.PreparedImportRow
import com.example.localledger.domain.importing.BillParseException
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.CandidateKind
import com.example.localledger.domain.importing.DuplicateFlag
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.importing.RowSkipReason
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class ImportRowState(
    val index: Int,
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long,
    val transactionAt: Long,
    val counterparty: String,
    val paymentMethod: String,
    val externalId: String,
    val goods: String,
    val remark: String,
    val categoryId: String?,
    val accountId: String?,
    val flag: DuplicateFlag,
    val mergeId: String?,
    val mergeKind: CandidateKind?,
    val mergeOrigin: String?,
    val selected: Boolean,
    val accountEdited: Boolean,
    val accountFromPaymentMethod: Boolean,
)

data class BillImportUiState(
    val loading: Boolean = false,
    val importing: Boolean = false,
    val fileName: String = "",
    val source: BillSource? = null,
    val rows: List<ImportRowState> = emptyList(),
    val skippedSummary: String = "",
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val defaultAccountId: String? = null,
    val error: String? = null,
    val resultMessage: String? = null,
)

@HiltViewModel
class BillImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSession: AppSession,
    private val importRepository: BillImportRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    val zoneId: ZoneId = dateRangeResolver.zoneId()
    private val _state = MutableStateFlow(BillImportUiState())
    val uiState: StateFlow<BillImportUiState> = _state
    private val _effects = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val effects: SharedFlow<String> = _effects

    fun onFile(uri: Uri) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, resultMessage = null, rows = emptyList()) }
            try {
                val fileName = displayName(uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw BillParseException("无法读取所选文件。")
                val parsed = importRepository.parse(bytes, fileName)
                val accounts = accountRepository.observeAccounts(ledgerId).first()
                val expenses = categoryRepository.observeCategories(ledgerId, com.example.localledger.domain.model.CategoryType.EXPENSE).first()
                val incomes = categoryRepository.observeCategories(ledgerId, com.example.localledger.domain.model.CategoryType.INCOME).first()
                val defaultAccountId = appSession.state.value.settings?.importAccountId(ledgerId, parsed.source.name)
                val prepared = importRepository.prepare(parsed, ledgerId, expenses + incomes, accounts, defaultAccountId)
                _state.update {
                    it.copy(
                        loading = false,
                        fileName = fileName,
                        source = parsed.source,
                        rows = prepared.map(::toState),
                        skippedSummary = skippedSummary(parsed.skipped),
                        accounts = accounts.filter { account -> !account.isArchived },
                        expenseCategories = expenses.filter { category -> !category.isArchived },
                        incomeCategories = incomes.filter { category -> !category.isArchived },
                        defaultAccountId = defaultAccountId,
                    )
                }
            } catch (error: BillParseException) {
                _state.update { it.copy(loading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "读取账单失败") }
            }
        }
    }

    fun toggle(index: Int) {
        _state.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.index != index || !row.canSelect) row else row.copy(selected = !row.selected)
                },
            )
        }
    }

    fun selectImportable() {
        _state.update { state ->
            state.copy(rows = state.rows.map { row -> row.copy(selected = row.flag == DuplicateFlag.NONE) })
        }
    }

    fun clearSelection() {
        _state.update { state -> state.copy(rows = state.rows.map { it.copy(selected = false) }) }
    }

    fun onCategory(index: Int, categoryId: String) {
        _state.update { state ->
            state.copy(rows = state.rows.map { if (it.index == index) it.copy(categoryId = categoryId) else it })
        }
    }

    fun onAccount(index: Int, accountId: String) {
        _state.update { state ->
            state.copy(
                rows = state.rows.map {
                    if (it.index == index) it.copy(accountId = accountId, accountEdited = true) else it
                },
            )
        }
    }

    fun onDefaultAccount(accountId: String) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        val source = _state.value.source ?: return
        viewModelScope.launch {
            appSession.updateSettings { settings ->
                val key = settings.importAccountKey(ledgerId, source.name)
                settings.copy(importAccountIdBySource = settings.importAccountIdBySource + (key to accountId))
            }
            _state.update { state ->
                state.copy(
                    defaultAccountId = accountId,
                    rows = state.rows.map { row ->
                        if (row.accountEdited || row.accountFromPaymentMethod) {
                            row
                        } else {
                            row.copy(accountId = accountId)
                        }
                    },
                )
            }
        }
    }

    fun importSelected() {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        val rows = _state.value.rows
        if (rows.none { it.selected }) {
            _effects.tryEmit("请先选择要导入的流水")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                val result = importRepository.commit(
                    ledgerId,
                    rows.map { row ->
                        CommitImportRow(
                            row = row.toParsed(),
                            categoryId = row.categoryId,
                            accountId = row.accountId,
                            flag = row.flag,
                            mergeId = row.mergeId,
                            mergeKind = row.mergeKind,
                            selected = row.selected,
                        )
                    },
                )
                val message = "已导入 ${result.imported} 笔，合并 ${result.merged} 笔，跳过 ${result.skipped} 笔"
                _state.update { it.copy(importing = false, rows = emptyList(), resultMessage = message, fileName = "") }
                _effects.tryEmit(message)
            } catch (error: Exception) {
                _state.update { it.copy(importing = false, error = error.message ?: "导入失败") }
            }
        }
    }

    private fun toState(prepared: PreparedImportRow): ImportRowState {
        val row = prepared.row
        return ImportRowState(
            index = prepared.index,
            source = row.source,
            type = row.type,
            amountMinor = row.amountMinor,
            transactionAt = row.transactionAt,
            counterparty = row.counterparty,
            paymentMethod = row.paymentMethod,
            externalId = row.externalId,
            goods = row.goods,
            remark = row.note,
            categoryId = prepared.categoryId,
            accountId = prepared.accountId,
            flag = prepared.flag,
            mergeId = prepared.hit?.candidateId,
            mergeKind = prepared.hit?.kind,
            mergeOrigin = prepared.hit?.origin,
            selected = prepared.flag == DuplicateFlag.NONE,
            accountEdited = false,
            accountFromPaymentMethod = prepared.accountFromPaymentMethod,
        )
    }

    private fun ImportRowState.toParsed() = com.example.localledger.domain.importing.ParsedBillRow(
        source = source,
        type = type,
        amountMinor = amountMinor,
        transactionAt = transactionAt,
        counterparty = counterparty,
        goods = goods,
        paymentMethod = paymentMethod,
        status = "",
        externalId = externalId,
        merchantOrderId = "",
        note = remark,
        keywordText = "",
    )

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun skippedSummary(skipped: List<com.example.localledger.domain.importing.SkippedBillRow>): String {
        if (skipped.isEmpty()) return ""
        val failed = skipped.count { it.reason == RowSkipReason.FAILED_OR_CLOSED }
        val neutral = skipped.count { it.reason == RowSkipReason.NEUTRAL }
        val invalid = skipped.count { it.reason == RowSkipReason.INVALID_AMOUNT || it.reason == RowSkipReason.INVALID_TIME }
        val parts = listOfNotNull(
            failed.takeIf { it > 0 }?.let { "失败或关闭 $it" },
            neutral.takeIf { it > 0 }?.let { "不计收支 $it" },
            invalid.takeIf { it > 0 }?.let { "无法识别 $it" },
        )
        return if (parts.isEmpty()) "已跳过 ${skipped.size} 条" else "已跳过 ${skipped.size} 条（${parts.joinToString("，")}）"
    }
}

private val ImportRowState.canSelect: Boolean
    get() = flag != DuplicateFlag.ALREADY_IMPORTED && flag != DuplicateFlag.IN_FILE_DUPLICATE

fun ImportRowState.flagLabel(): String? = when (flag) {
    DuplicateFlag.NONE -> null
    DuplicateFlag.ALREADY_IMPORTED -> "已导入"
    DuplicateFlag.IN_FILE_DUPLICATE -> "文件内重复"
    DuplicateFlag.LIKELY_DUPLICATE -> when {
        mergeKind == CandidateKind.DRAFT || mergeOrigin == RecordOrigin.NOTIFICATION -> "可能重复，勾选后合并"
        else -> "可能重复"
    }
}
