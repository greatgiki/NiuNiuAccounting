package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.DraftStatus
import com.example.localledger.domain.importing.ImportNote
import com.example.localledger.domain.importing.MerchantMemoryPolicy
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PendingDraft(
    val id: String,
    val ledgerId: String,
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long,
    val counterparty: String,
    val note: String?,
    val rawText: String,
    val categoryId: String?,
    val accountId: String?,
    val transactionAt: Long,
    val likelyDuplicate: Boolean,
    val needsAmount: Boolean = false,
    val toAccountId: String? = null,
    val sourceKey: String = "",
)

data class NewDraft(
    val ledgerId: String,
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long,
    val counterparty: String,
    val goods: String = "",
    val remark: String = "",
    val rawText: String,
    val categoryId: String?,
    val accountId: String?,
    val transactionAt: Long,
    val notificationKey: String?,
    val likelyDuplicate: Boolean,
    val needsAmount: Boolean = false,
    val sbnKey: String? = null,
    val groupKey: String? = null,
    val toAccountId: String? = null,
)

sealed interface DraftConfirmResult {
    data class Confirmed(val transactionId: String) : DraftConfirmResult
    data object MissingAssignment : DraftConfirmResult
    data object NeedsAmount : DraftConfirmResult
    data object NotPending : DraftConfirmResult
}

@Singleton
class DraftRepository @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
    private val merchantCategories: MerchantCategoryBook,
) {
    fun observePending(ledgerId: String): Flow<List<PendingDraft>> = flow {
        emitAll(
            databaseProvider.get().pendingDraftDao().observePending(ledgerId)
                .map { rows -> rows.map { it.toDraft() } },
        )
    }

    fun observePendingCount(ledgerId: String): Flow<Int> = flow {
        emitAll(databaseProvider.get().pendingDraftDao().observePendingCount(ledgerId))
    }

    suspend fun findByNotificationKey(key: String): PendingDraft? {
        return databaseProvider.get().pendingDraftDao().findByNotificationKey(key)?.toDraft()
    }

    suspend fun insert(draft: NewDraft): String = writeOperation {
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        databaseProvider.get().pendingDraftDao().upsert(
            PendingDraftEntity(
                id = id,
                ledgerId = draft.ledgerId,
                source = draft.source.name,
                type = draft.type,
                amountMinor = draft.amountMinor,
                counterparty = draft.counterparty.ifBlank { null },
                note = ImportNote.compose(draft.counterparty, draft.goods, draft.remark),
                rawText = draft.rawText.take(500),
                categoryId = draft.categoryId,
                accountId = draft.accountId,
                transactionAt = draft.transactionAt,
                status = DraftStatus.PENDING,
                transactionId = null,
                notificationKey = draft.notificationKey,
                likelyDuplicate = draft.likelyDuplicate,
                createdAt = now,
                updatedAt = now,
                needsAmount = draft.needsAmount,
                sbnKey = draft.sbnKey,
                groupKey = draft.toAccountId?.let { "to:$it" } ?: draft.groupKey,
            ),
        )
        id
    }

    suspend fun completeAmount(
        id: String,
        amountMinor: Long,
        counterparty: String,
        rawText: String,
        likelyDuplicate: Boolean,
    ): Boolean {
        if (amountMinor <= 0L) return false
        val dao = databaseProvider.get().pendingDraftDao()
        val existing = dao.findById(id) ?: return false
        if (existing.status != DraftStatus.PENDING || !existing.needsAmount) return false
        val party = counterparty.ifBlank { existing.counterparty.orEmpty() }
        val updated = dao.completeAmount(
            id = id,
            amountMinor = amountMinor,
            counterparty = party.ifBlank { null },
            note = ImportNote.compose(party, "", ""),
            rawText = rawText.take(500),
            likelyDuplicate = likelyDuplicate || existing.likelyDuplicate,
            updatedAt = clock.millis(),
        )
        return updated > 0
    }

    suspend fun updateAmount(id: String, amountMinor: Long): Boolean {
        if (amountMinor <= 0L) return false
        val updated = databaseProvider.get().pendingDraftDao().setAmount(id, amountMinor, clock.millis())
        return updated > 0
    }

    suspend fun updateAssignment(id: String, categoryId: String?, accountId: String?) {
        val database = databaseProvider.get()
        val existing = database.pendingDraftDao().findById(id) ?: return
        database.pendingDraftDao().updateAssignment(id, categoryId, accountId, clock.millis())
        val changed = categoryId != existing.categoryId
        if (
            MerchantMemoryPolicy.shouldRemember(
                type = existing.type,
                merchant = existing.counterparty,
                categoryId = categoryId,
                previousCategoryId = existing.categoryId,
                categoryChanged = changed,
            )
        ) {
            merchantCategories.remember(existing.ledgerId, existing.type, existing.counterparty.orEmpty(), categoryId!!)
        }
    }

    suspend fun dismiss(id: String) {
        val dao = databaseProvider.get().pendingDraftDao()
        val existing = dao.findById(id) ?: return
        if (existing.status != DraftStatus.PENDING) return
        dao.updateStatus(id, DraftStatus.DISMISSED, null, clock.millis())
    }

    suspend fun restore(id: String): Boolean {
        val dao = databaseProvider.get().pendingDraftDao()
        val existing = dao.findById(id) ?: return false
        if (existing.status != DraftStatus.DISMISSED) return false
        dao.updateStatus(id, DraftStatus.PENDING, null, clock.millis())
        return true
    }

    suspend fun confirm(id: String): DraftConfirmResult = writeOperation {
        val database = databaseProvider.get()
        val existing = database.pendingDraftDao().findById(id) ?: return@writeOperation DraftConfirmResult.NotPending
        if (existing.status != DraftStatus.PENDING) return@writeOperation DraftConfirmResult.NotPending
        if (existing.needsAmount || existing.amountMinor <= 0L) return@writeOperation DraftConfirmResult.NeedsAmount
        val accountId = existing.accountId
        val transactionId = if (existing.type == TransactionType.TRANSFER) {
            val toAccountId = transferTarget(existing.groupKey)
            if (accountId == null || toAccountId.isNullOrBlank() || toAccountId == accountId) {
                return@writeOperation DraftConfirmResult.MissingAssignment
            }
            transactionRepository.saveTransaction(
                SaveTransactionCommand(
                    ledgerId = existing.ledgerId,
                    type = existing.type,
                    amountMinor = existing.amountMinor,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    transactionAt = existing.transactionAt,
                    note = existing.note ?: existing.counterparty,
                    origin = RecordOrigin.forDraft(existing.source, existing.notificationKey),
                ),
            )
        } else {
            val categoryId = existing.categoryId
            if (categoryId == null || accountId == null) return@writeOperation DraftConfirmResult.MissingAssignment
            transactionRepository.saveTransaction(
                SaveTransactionCommand(
                    ledgerId = existing.ledgerId,
                    type = existing.type,
                    amountMinor = existing.amountMinor,
                    accountId = accountId,
                    categoryId = categoryId,
                    transactionAt = existing.transactionAt,
                    note = existing.note ?: existing.counterparty,
                    origin = RecordOrigin.forDraft(existing.source, existing.notificationKey),
                    counterparty = existing.counterparty,
                ),
            )
        }
        database.pendingDraftDao().updateStatus(id, DraftStatus.CONFIRMED, transactionId, clock.millis())
        DraftConfirmResult.Confirmed(transactionId)
    }

    suspend fun markMerged(id: String, transactionId: String) {
        databaseProvider.get().pendingDraftDao().updateStatus(id, DraftStatus.MERGED, transactionId, clock.millis())
    }

    suspend fun updateTransfer(id: String, accountId: String?, toAccountId: String?) {
        val groupKey = toAccountId?.let { "to:$it" }
        databaseProvider.get().pendingDraftDao().updateTransfer(id, accountId, groupKey, clock.millis())
    }

    suspend fun updateCapture(
        id: String,
        type: TransactionType,
        categoryId: String?,
        accountId: String?,
        toAccountId: String?,
    ) {
        val database = databaseProvider.get()
        val existing = database.pendingDraftDao().findById(id) ?: return
        val transfer = type == TransactionType.TRANSFER
        val groupKey = if (transfer) {
            toAccountId?.let { "to:$it" }
        } else {
            existing.groupKey?.takeUnless { it.startsWith("to:") }
        }
        val storedCategory = if (transfer) null else categoryId
        database.pendingDraftDao().updateCapture(
            id = id,
            type = type,
            categoryId = storedCategory,
            accountId = accountId,
            groupKey = groupKey,
            updatedAt = clock.millis(),
        )
        val changed = storedCategory != existing.categoryId
        if (
            MerchantMemoryPolicy.shouldRemember(
                type = type,
                merchant = existing.counterparty,
                categoryId = storedCategory,
                previousCategoryId = existing.categoryId,
                categoryChanged = changed,
            )
        ) {
            merchantCategories.remember(existing.ledgerId, type, existing.counterparty.orEmpty(), storedCategory!!)
        }
    }

    private fun PendingDraftEntity.toDraft(): PendingDraft {
        val source = runCatching { BillSource.valueOf(source) }.getOrDefault(BillSource.WECHAT)
        return PendingDraft(
            id = id,
            ledgerId = ledgerId,
            source = source,
            type = type,
            amountMinor = amountMinor,
            counterparty = counterparty.orEmpty(),
            note = note,
            rawText = rawText,
            categoryId = categoryId,
            accountId = accountId,
            transactionAt = transactionAt,
            likelyDuplicate = likelyDuplicate,
            needsAmount = needsAmount,
            toAccountId = transferTarget(groupKey),
            sourceKey = sbnKey.orEmpty(),
        )
    }

    private fun transferTarget(groupKey: String?): String? {
        return groupKey?.takeIf { it.startsWith("to:") }?.removePrefix("to:")?.takeIf { it.isNotBlank() }
    }
}
