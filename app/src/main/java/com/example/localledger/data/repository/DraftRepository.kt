package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.importing.DraftStatus
import com.example.localledger.domain.importing.ImportNote
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
)

sealed interface DraftConfirmResult {
    data class Confirmed(val transactionId: String) : DraftConfirmResult
    data object MissingAssignment : DraftConfirmResult
    data object NotPending : DraftConfirmResult
}

@Singleton
class DraftRepository @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
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
            ),
        )
        id
    }

    suspend fun updateAssignment(id: String, categoryId: String?, accountId: String?) {
        databaseProvider.get().pendingDraftDao().updateAssignment(id, categoryId, accountId, clock.millis())
    }

    suspend fun dismiss(id: String) {
        val dao = databaseProvider.get().pendingDraftDao()
        val existing = dao.findById(id) ?: return
        if (existing.status != DraftStatus.PENDING) return
        dao.updateStatus(id, DraftStatus.DISMISSED, null, clock.millis())
    }

    suspend fun confirm(id: String): DraftConfirmResult = writeOperation {
        val database = databaseProvider.get()
        val existing = database.pendingDraftDao().findById(id) ?: return@writeOperation DraftConfirmResult.NotPending
        if (existing.status != DraftStatus.PENDING) return@writeOperation DraftConfirmResult.NotPending
        val categoryId = existing.categoryId
        val accountId = existing.accountId
        if (categoryId == null || accountId == null) return@writeOperation DraftConfirmResult.MissingAssignment
        val transactionId = transactionRepository.saveTransaction(
            SaveTransactionCommand(
                ledgerId = existing.ledgerId,
                type = existing.type,
                amountMinor = existing.amountMinor,
                accountId = accountId,
                categoryId = categoryId,
                transactionAt = existing.transactionAt,
                note = existing.note ?: existing.counterparty,
                origin = RecordOrigin.NOTIFICATION,
            ),
        )
        database.pendingDraftDao().updateStatus(id, DraftStatus.CONFIRMED, transactionId, clock.millis())
        DraftConfirmResult.Confirmed(transactionId)
    }

    suspend fun markMerged(id: String, transactionId: String) {
        databaseProvider.get().pendingDraftDao().updateStatus(id, DraftStatus.MERGED, transactionId, clock.millis())
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
        )
    }
}
