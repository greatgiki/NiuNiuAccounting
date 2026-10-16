package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.PendingDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDraftDao {
    @Upsert
    suspend fun upsert(entity: PendingDraftEntity)

    @Query("SELECT * FROM pending_drafts WHERE id = :id")
    suspend fun findById(id: String): PendingDraftEntity?

    @Query("SELECT * FROM pending_drafts WHERE notification_key = :key")
    suspend fun findByNotificationKey(key: String): PendingDraftEntity?

    @Query(
        """
        SELECT * FROM pending_drafts
        WHERE ledger_id = :ledgerId AND status = 'PENDING'
        ORDER BY transaction_at DESC, created_at DESC
        """,
    )
    fun observePending(ledgerId: String): Flow<List<PendingDraftEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM pending_drafts
        WHERE ledger_id = :ledgerId AND status = 'PENDING'
        """,
    )
    fun observePendingCount(ledgerId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM pending_drafts
        WHERE ledger_id = :ledgerId
          AND status = 'PENDING'
          AND transaction_at >= :startInclusive
          AND transaction_at < :endExclusive
        """,
    )
    suspend fun listPendingInRange(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PendingDraftEntity>

    @Query(
        """
        SELECT * FROM pending_drafts
        WHERE ledger_id = :ledgerId
          AND transaction_at >= :startInclusive
          AND transaction_at < :endExclusive
          AND status != 'DISMISSED'
        """,
    )
    suspend fun listActiveInRange(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PendingDraftEntity>

    @Query(
        """
        UPDATE pending_drafts
        SET status = :status, transaction_id = :transactionId, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: String, status: String, transactionId: String?, updatedAt: Long)

    @Query(
        """
        UPDATE pending_drafts
        SET category_id = :categoryId, account_id = :accountId, updated_at = :updatedAt
        WHERE id = :id AND status = 'PENDING'
        """,
    )
    suspend fun updateAssignment(id: String, categoryId: String?, accountId: String?, updatedAt: Long)
}
