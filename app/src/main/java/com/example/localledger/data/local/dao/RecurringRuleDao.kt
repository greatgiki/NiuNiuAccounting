package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.model.RecurringRuleRow
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query(
        """
        SELECT r.*,
            source.name AS account_name,
            destination.name AS to_account_name,
            c.name AS category_name,
            source.currency_code AS account_currency_code
        FROM recurring_rules r
        JOIN accounts source ON source.id = r.account_id
        LEFT JOIN accounts destination ON destination.id = r.to_account_id
        LEFT JOIN categories c ON c.id = r.category_id
        WHERE r.ledger_id = :ledgerId
        ORDER BY r.paused ASC, r.next_run_date ASC, r.created_at ASC
        """,
    )
    fun observe(ledgerId: String): Flow<List<RecurringRuleRow>>

    @Query(
        """
        SELECT r.*,
            source.name AS account_name,
            destination.name AS to_account_name,
            c.name AS category_name,
            source.currency_code AS account_currency_code
        FROM recurring_rules r
        JOIN accounts source ON source.id = r.account_id
        LEFT JOIN accounts destination ON destination.id = r.to_account_id
        LEFT JOIN categories c ON c.id = r.category_id
        WHERE r.paused = 0
        ORDER BY r.next_run_date ASC
        """,
    )
    suspend fun listActive(): List<RecurringRuleRow>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun findById(id: String): RecurringRuleEntity?

    @Upsert
    suspend fun upsert(entity: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET paused = :paused, updated_at = :updatedAt WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun setPaused(id: String, ledgerId: String, paused: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM recurring_rules WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun delete(id: String, ledgerId: String): Int

    @Query("SELECT transaction_id FROM recurring_occurrences WHERE rule_id = :ruleId")
    suspend fun occurrenceTransactionIds(ruleId: String): List<String>

    @Query("UPDATE recurring_rules SET next_run_date = :nextRunDate, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateNextRun(id: String, nextRunDate: String, updatedAt: Long)

    @Query(
        """
        SELECT COUNT(*) FROM recurring_occurrences
        WHERE rule_id = :ruleId AND occurrence_date = :occurrenceDate
        """,
    )
    suspend fun countOccurrence(ruleId: String, occurrenceDate: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(entity: RecurringOccurrenceEntity): Long
}
