package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.importing.AccountHintMapper
import com.example.localledger.domain.importing.BillFileParser
import com.example.localledger.domain.importing.BillParseResult
import com.example.localledger.domain.importing.CandidateKind
import com.example.localledger.domain.importing.CategoryKeywordMapper
import com.example.localledger.domain.importing.DraftStatus
import com.example.localledger.domain.importing.DuplicateFlag
import com.example.localledger.domain.importing.ExternalKey
import com.example.localledger.domain.importing.FuzzyHit
import com.example.localledger.domain.importing.ImportMatcher
import com.example.localledger.domain.importing.ImportNote
import com.example.localledger.domain.importing.MatchCandidate
import com.example.localledger.domain.importing.ParsedBillRow
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.ZoneIdProvider
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedImportRow(
    val index: Int,
    val row: ParsedBillRow,
    val categoryId: String?,
    val accountId: String?,
    val accountFromPaymentMethod: Boolean,
    val flag: DuplicateFlag,
    val hit: FuzzyHit?,
)

data class CommitImportRow(
    val row: ParsedBillRow,
    val categoryId: String?,
    val accountId: String?,
    val flag: DuplicateFlag,
    val mergeId: String?,
    val mergeKind: CandidateKind?,
    val selected: Boolean,
)

data class ImportCommitResult(
    val imported: Int,
    val merged: Int,
    val skipped: Int,
)

@Singleton
class BillImportRepository @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
    private val zoneIdProvider: ZoneIdProvider,
) {
    fun parse(bytes: ByteArray, fileName: String): BillParseResult {
        return BillFileParser.parse(bytes, fileName, zoneIdProvider.current())
    }

    suspend fun prepare(
        parsed: BillParseResult,
        ledgerId: String,
        categories: List<Category>,
        accounts: List<Account>,
        defaultAccountId: String?,
    ): List<PreparedImportRow> {
        if (parsed.rows.isEmpty()) return emptyList()
        val database = databaseProvider.get()
        val known = database.transactionDao().listExternalKeys(ledgerId)
            .map { ExternalKey(it.externalSource, it.externalId) }
            .toMutableSet()
        val start = parsed.rows.minOf { it.transactionAt } - ImportMatcher.WINDOW_MILLIS
        val end = parsed.rows.maxOf { it.transactionAt } + ImportMatcher.WINDOW_MILLIS + 1
        val transactions = database.transactionDao().listFuzzyCandidates(ledgerId, start, end).map { row ->
            MatchCandidate(
                id = row.id,
                kind = CandidateKind.TRANSACTION,
                type = row.type,
                amountMinor = row.amountMinor,
                transactionAt = row.transactionAt,
                origin = row.origin,
                externalSource = row.externalSource,
                externalId = row.externalId,
            )
        }
        val drafts = database.pendingDraftDao().listPendingInRange(ledgerId, start, end).map { draft ->
            MatchCandidate(
                id = draft.id,
                kind = CandidateKind.DRAFT,
                type = draft.type,
                amountMinor = draft.amountMinor,
                transactionAt = draft.transactionAt,
                origin = RecordOrigin.NOTIFICATION,
                externalSource = null,
                externalId = null,
            )
        }
        val candidates = transactions + drafts
        val seenInFile = mutableSetOf<ExternalKey>()
        return parsed.rows.mapIndexed { index, row ->
            val key = row.externalId.trim()
            val inFile = key.isNotEmpty() && ExternalKey(row.source.name, key) in seenInFile
            val match = if (inFile) {
                com.example.localledger.domain.importing.MatchResult(DuplicateFlag.IN_FILE_DUPLICATE, null)
            } else {
                ImportMatcher.match(
                    source = row.source,
                    externalId = key,
                    type = row.type,
                    amountMinor = row.amountMinor,
                    transactionAt = row.transactionAt,
                    knownExternalIds = known,
                    candidates = candidates,
                )
            }
            if (key.isNotEmpty()) seenInFile += ExternalKey(row.source.name, key)
            val account = AccountHintMapper.suggest(row.paymentMethod, row.source, accounts, defaultAccountId)
            PreparedImportRow(
                index = index,
                row = row,
                categoryId = resolveCategory(categories, row.type, CategoryKeywordMapper.suggestName(row.type, row.keywordText)),
                accountId = account.accountId,
                accountFromPaymentMethod = account.matchedPaymentMethod,
                flag = match.flag,
                hit = match.hit,
            )
        }
    }

    suspend fun commit(ledgerId: String, rows: List<CommitImportRow>): ImportCommitResult = writeOperation {
        val database = databaseProvider.get()
        val known = database.transactionDao().listExternalKeys(ledgerId)
            .map { ExternalKey(it.externalSource, it.externalId) }
            .toMutableSet()
        var imported = 0
        var merged = 0
        var skipped = 0
        for (item in rows) {
            if (!item.selected || item.flag == DuplicateFlag.ALREADY_IMPORTED || item.flag == DuplicateFlag.IN_FILE_DUPLICATE) {
                skipped += 1
                continue
            }
            val externalId = item.row.externalId.trim().ifEmpty { null }
            val sourceName = item.row.source.name
            if (externalId != null && ExternalKey(sourceName, externalId) in known) {
                skipped += 1
                continue
            }
            val categoryId = item.categoryId
            val accountId = item.accountId
            if (categoryId == null || accountId == null) {
                skipped += 1
                continue
            }
            val mergedId = mergeIfNeeded(ledgerId, item, externalId, sourceName)
            if (mergedId != null) {
                if (externalId != null) known += ExternalKey(sourceName, externalId)
                merged += 1
                continue
            }
            val transactionId = transactionRepository.saveTransaction(
                SaveTransactionCommand(
                    ledgerId = ledgerId,
                    type = item.row.type,
                    amountMinor = item.row.amountMinor,
                    accountId = accountId,
                    categoryId = categoryId,
                    transactionAt = item.row.transactionAt,
                    note = ImportNote.compose(item.row.counterparty, item.row.goods, item.row.note),
                    externalSource = if (externalId == null) null else sourceName,
                    externalId = externalId,
                    origin = RecordOrigin.IMPORT,
                ),
            )
            if (externalId != null) known += ExternalKey(sourceName, externalId)
            if (item.mergeKind == CandidateKind.DRAFT && item.mergeId != null) {
                val draft = database.pendingDraftDao().findById(item.mergeId)
                if (draft?.status == DraftStatus.PENDING) {
                    database.pendingDraftDao().updateStatus(item.mergeId, DraftStatus.MERGED, transactionId, clock.millis())
                }
            }
            imported += 1
        }
        ImportCommitResult(imported, merged, skipped)
    }

    private suspend fun mergeIfNeeded(
        ledgerId: String,
        item: CommitImportRow,
        externalId: String?,
        sourceName: String,
    ): String? {
        if (item.flag != DuplicateFlag.LIKELY_DUPLICATE) return null
        val mergeId = item.mergeId ?: return null
        val database = databaseProvider.get()
        return when (item.mergeKind) {
            CandidateKind.DRAFT -> {
                val draft = database.pendingDraftDao().findById(mergeId) ?: return null
                val transactionId = when (draft.status) {
                    DraftStatus.CONFIRMED, DraftStatus.MERGED -> draft.transactionId
                    else -> null
                } ?: return null
                attach(ledgerId, transactionId, sourceName, externalId)
            }
            CandidateKind.TRANSACTION -> {
                val existing = database.transactionDao().findByIdInLedger(mergeId, ledgerId) ?: return null
                if (existing.origin != RecordOrigin.NOTIFICATION || existing.externalId != null) return null
                attach(ledgerId, existing.id, sourceName, externalId) ?: existing.id
            }
            null -> null
        }
    }

    private suspend fun attach(ledgerId: String, transactionId: String, sourceName: String, externalId: String?): String? {
        if (externalId == null) return transactionId
        val updated = databaseProvider.get().transactionDao().attachExternalId(
            id = transactionId,
            ledgerId = ledgerId,
            source = sourceName,
            externalId = externalId,
            updatedAt = clock.millis(),
        )
        return if (updated == 1) transactionId else null
    }

    private fun resolveCategory(categories: List<Category>, type: TransactionType, name: String): String? {
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val active = categories.filter { !it.isArchived && it.type == categoryType }
        return active.firstOrNull { it.name == name }?.id ?: active.firstOrNull { it.name == "其他" }?.id
    }
}
