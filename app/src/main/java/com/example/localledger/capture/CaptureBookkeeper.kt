package com.example.localledger.capture

import android.util.Log
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.repository.DraftRepository
import com.example.localledger.data.repository.NewDraft
import com.example.localledger.domain.capture.CaptureDedup
import com.example.localledger.domain.capture.CaptureSelection
import com.example.localledger.domain.capture.CaptureSheet
import com.example.localledger.domain.capture.DuplicatePlace
import com.example.localledger.domain.capture.LearnedAccount
import com.example.localledger.domain.capture.MoneySighting
import com.example.localledger.domain.capture.NamedChoice
import com.example.localledger.domain.capture.ParsedCapture
import com.example.localledger.domain.capture.SightPlace
import com.example.localledger.domain.importing.AccountHintMapper
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.CategoryKeywordMapper
import com.example.localledger.domain.importing.DraftStatus
import com.example.localledger.domain.importing.MerchantKey
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.data.repository.MerchantCategoryBook
import com.example.localledger.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BookResult {
    data class Saved(val transactionId: String, val already: Boolean = false) : BookResult
    data class Drafted(val draftId: String) : BookResult
    data class Failed(val message: String) : BookResult
}

@Singleton
class CaptureBookkeeper @Inject constructor(
    private val settingsStore: SettingsStore,
    private val databaseProvider: WalletDatabaseSource,
    private val draftRepository: DraftRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantCategories: MerchantCategoryBook,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {
    suspend fun prepare(parsed: ParsedCapture): CaptureSheet? {
        val settings = settingsStore.peek() ?: return null
        if (!settings.autoRecordEnabled) return null
        if (parsed.source == BillSource.WECHAT && !settings.wechatNotificationEnabled) return null
        if (parsed.source == BillSource.ALIPAY && !settings.alipayNotificationEnabled) return null
        if (parsed.source == BillSource.UNIONPAY && !settings.unionPayEnabled) return null
        if (parsed.source == BillSource.BANK && !settings.bankSmsEnabled) return null
        val ledgerId = settings.captureLedgerId.ifBlank { settings.currentLedgerId }
        if (ledgerId.isBlank()) return null
        val accounts = accountRepository.observeAccounts(ledgerId).first().filter { !it.isArchived }
        val expenses = categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE).first()
            .filter { !it.isArchived }
        val incomes = categoryRepository.observeCategories(ledgerId, CategoryType.INCOME).first()
            .filter { !it.isArchived }
        val pool = if (parsed.type == TransactionType.INCOME) incomes else expenses
        val remembered = merchantCategories.findCategoryId(ledgerId, parsed.type, parsed.merchant)
        val keywordId = CategoryKeywordMapper.match(parsed.type, parsed.merchant)?.let { hit ->
            pool.firstOrNull { it.name == hit.categoryName }?.id
        }
        val configuredDefault = when (parsed.type) {
            TransactionType.EXPENSE -> settings.captureExpenseCategoryId
            TransactionType.INCOME -> settings.captureIncomeCategoryId
            TransactionType.TRANSFER -> ""
        }
        val defaultId = configuredDefault.takeIf { id -> pool.any { it.id == id } }
        val otherId = pool.firstOrNull { it.name == "其他" }?.id
        val categoryId = if (parsed.type == TransactionType.TRANSFER) {
            null
        } else {
            remembered ?: keywordId ?: defaultId ?: otherId
        }
        val learnedKey = LearnedAccount.key(ledgerId, parsed.type, parsed.merchant)
        val learned = settings.learnedAccountByKey[learnedKey]?.takeIf { id -> accounts.any { it.id == id } }
        val preferred = settings.captureAccountId.ifBlank {
            settings.importAccountId(ledgerId, parsed.source.name).orEmpty()
        }.ifBlank { settings.lastAccountIdByLedger[ledgerId].orEmpty() }
        val hinted = AccountHintMapper.suggest(
            paymentMethod = parsed.accountLabel,
            source = parsed.source,
            accounts = accounts,
            defaultAccountId = preferred.ifBlank { null },
        ).accountId
        val accountId = learned ?: hinted
        val transferTo = settings.captureTransferAccountId
            .takeIf { id -> id.isNotBlank() && id != accountId && accounts.any { it.id == id } }
            ?: accounts.firstOrNull { it.id != accountId }?.id
        val now = clock.millis()
        val hit = sightings(ledgerId, now).let { rows -> CaptureDedup.match(parsed.amountMinor, now, rows) }
        val duplicate = when (hit?.place) {
            SightPlace.DRAFT -> DuplicatePlace.DRAFT
            SightPlace.TRANSACTION -> DuplicatePlace.TRANSACTION
            null -> DuplicatePlace.NONE
        }
        return CaptureSheet(
            token = UUID.randomUUID().toString(),
            source = parsed.source,
            page = parsed.page,
            type = parsed.type,
            amountMinor = parsed.amountMinor,
            merchant = parsed.merchant,
            accountLabel = parsed.accountLabel,
            accountId = accountId,
            toAccountId = if (parsed.type == TransactionType.TRANSFER) transferTo else null,
            categoryId = categoryId,
            suggestedCategoryId = categoryId,
            transactionAt = parsed.occurredAt,
            ledgerId = ledgerId,
            ruleId = parsed.ruleId,
            duplicate = duplicate,
            duplicateDraftId = hit?.draftId,
            duplicateText = when (duplicate) {
                DuplicatePlace.DRAFT -> "一分钟内已有相同金额的待确认。记下会用这一笔，不会再记一条。"
                DuplicatePlace.TRANSACTION -> "一分钟内记过相同金额。点「仍要记下」才会再记一笔。"
                DuplicatePlace.NONE -> ""
            },
            accounts = accounts.map { NamedChoice(it.id, it.name) },
            expenseCategories = expenses.map { NamedChoice(it.id, it.name) },
            incomeCategories = incomes.map { NamedChoice(it.id, it.name) },
            aiEligible = parsed.type != TransactionType.TRANSFER &&
                parsed.merchant.isNotBlank() &&
                remembered == null &&
                keywordId == null &&
                (categoryId == null || categoryId == otherId),
        )
    }

    suspend fun save(sheet: CaptureSheet, selection: CaptureSelection): BookResult {
        return try {
            val raced = if (sheet.duplicate != DuplicatePlace.TRANSACTION) recentTransaction(sheet) else null
            if (raced != null) {
                retireDrafts(sheet, raced, keep = null)
                return BookResult.Saved(raced, already = true)
            }
            val reused = reuseDraft(sheet, selection)
            if (reused != null) {
                remember(sheet, selection)
                retireDrafts(sheet, reused, keep = sheet.duplicateDraftId)
                return BookResult.Saved(reused)
            }
            val id = transactionRepository.saveTransaction(command(sheet, selection))
            remember(sheet, selection)
            retireDrafts(sheet, id, keep = null)
            BookResult.Saved(id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "确认记账失败", error)
            later(sheet, selection)
        }
    }

    suspend fun later(sheet: CaptureSheet, selection: CaptureSelection): BookResult {
        return try {
            val existing = sheet.duplicateDraftId
            if (existing != null) {
                applySelection(existing, selection)
                return BookResult.Drafted(existing)
            }
            val id = draftRepository.insert(newDraft(sheet, selection))
            BookResult.Drafted(id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val key = notificationKey(sheet)
            val found = runCatching { draftRepository.findByNotificationKey(key) }.getOrNull()
            if (found != null) {
                BookResult.Drafted(found.id)
            } else {
                Log.e(TAG, "放入待确认失败", error)
                BookResult.Failed("没能放进待确认")
            }
        }
    }

    private suspend fun reuseDraft(sheet: CaptureSheet, selection: CaptureSelection): String? {
        val draftId = sheet.duplicateDraftId ?: return null
        if (sheet.duplicate != DuplicatePlace.DRAFT || selection.type != sheet.type) return null
        applySelection(draftId, selection)
        return when (val result = draftRepository.confirm(draftId)) {
            is com.example.localledger.data.repository.DraftConfirmResult.Confirmed -> result.transactionId
            else -> null
        }
    }

    private suspend fun applySelection(draftId: String, selection: CaptureSelection) {
        val transfer = selection.type == TransactionType.TRANSFER
        draftRepository.updateCapture(
            id = draftId,
            type = selection.type,
            categoryId = if (transfer) null else selection.categoryId,
            accountId = selection.accountId,
            toAccountId = if (transfer) selection.toAccountId else null,
        )
    }

    private fun command(sheet: CaptureSheet, selection: CaptureSelection): SaveTransactionCommand {
        val transfer = selection.type == TransactionType.TRANSFER
        val rememberCategory = !transfer &&
            selection.categoryTouched &&
            !selection.categoryId.isNullOrBlank() &&
            selection.categoryId != sheet.suggestedCategoryId
        return SaveTransactionCommand(
            ledgerId = sheet.ledgerId,
            type = selection.type,
            amountMinor = sheet.amountMinor,
            accountId = selection.accountId.orEmpty(),
            toAccountId = if (transfer) selection.toAccountId else null,
            categoryId = if (transfer) null else selection.categoryId,
            transactionAt = sheet.transactionAt,
            note = sheet.merchant.ifBlank { null },
            origin = if (sheet.source == BillSource.BANK) RecordOrigin.SMS else RecordOrigin.ACCESSIBILITY,
            counterparty = sheet.merchant,
            rememberMerchantCategory = rememberCategory,
        )
    }

    private fun newDraft(sheet: CaptureSheet, selection: CaptureSelection): NewDraft {
        val transfer = selection.type == TransactionType.TRANSFER
        return NewDraft(
            ledgerId = sheet.ledgerId,
            source = sheet.source,
            type = selection.type,
            amountMinor = sheet.amountMinor,
            counterparty = sheet.merchant,
            rawText = summary(sheet),
            categoryId = if (transfer) null else selection.categoryId,
            accountId = selection.accountId,
            transactionAt = sheet.transactionAt,
            notificationKey = notificationKey(sheet),
            likelyDuplicate = sheet.duplicate != DuplicatePlace.NONE,
            toAccountId = if (transfer) selection.toAccountId else null,
        )
    }

    private suspend fun remember(sheet: CaptureSheet, selection: CaptureSelection) {
        val accountId = selection.accountId ?: return
        val key = LearnedAccount.key(sheet.ledgerId, selection.type, sheet.merchant)
        if (key.isEmpty()) return
        settingsStore.update { current ->
            current.copy(learnedAccountByKey = LearnedAccount.remember(current.learnedAccountByKey, key, accountId))
        }
    }

    private suspend fun recentTransaction(sheet: CaptureSheet): String? {
        val now = clock.millis()
        return databaseProvider.get().transactionDao()
            .listFuzzyCandidates(sheet.ledgerId, now - CaptureDedup.WINDOW_MILLIS, now + 1)
            .firstOrNull { it.amountMinor == sheet.amountMinor }
            ?.id
    }

    private suspend fun retireDrafts(sheet: CaptureSheet, transactionId: String, keep: String?) {
        val now = clock.millis()
        val dao = databaseProvider.get().pendingDraftDao()
        dao.listCreatedSince(sheet.ledgerId, now - CaptureDedup.WINDOW_MILLIS).forEach { row ->
            if (row.status != DraftStatus.PENDING || row.amountMinor != sheet.amountMinor || row.id == keep) return@forEach
            dao.updateStatus(row.id, DraftStatus.MERGED, transactionId, now)
        }
    }

    private suspend fun sightings(ledgerId: String, now: Long): List<MoneySighting> {
        val start = now - CaptureDedup.WINDOW_MILLIS
        val database = databaseProvider.get()
        val rows = ArrayList<MoneySighting>()
        database.transactionDao().listFuzzyCandidates(ledgerId, start, now + 1).forEach { row ->
            rows += MoneySighting(row.amountMinor, row.transactionAt, SightPlace.TRANSACTION)
        }
        database.pendingDraftDao().listCreatedSince(ledgerId, start).forEach { row ->
            if (row.status != DraftStatus.PENDING || row.needsAmount || row.amountMinor <= 0L) return@forEach
            rows += MoneySighting(row.amountMinor, row.createdAt, SightPlace.DRAFT, row.id)
            rows += MoneySighting(row.amountMinor, row.transactionAt, SightPlace.DRAFT, row.id)
        }
        return rows
    }

    private fun notificationKey(sheet: CaptureSheet): String {
        val minute = sheet.transactionAt / 60_000L
        return "screen:${sheet.source.name}:${sheet.amountMinor}:$minute:${MerchantKey.normalize(sheet.merchant)}"
    }

    private fun summary(sheet: CaptureSheet): String {
        return "${sheet.source.label()} ${sheet.ruleId} ${sheet.merchant} ${sheet.accountLabel}".take(500)
    }

    private companion object {
        const val TAG = "NiuNiuCapture"
    }
}
