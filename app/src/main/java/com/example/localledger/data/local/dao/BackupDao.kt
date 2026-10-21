package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.ExchangeRateHistoryEntity
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.entity.TransactionEntity

@Dao
interface BackupDao {
    @Query("SELECT * FROM ledgers ORDER BY created_at ASC")
    suspend fun ledgers(): List<LedgerEntity>

    @Query("SELECT * FROM accounts ORDER BY created_at ASC")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM categories ORDER BY created_at ASC")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM transactions ORDER BY transaction_at ASC, created_at ASC")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT * FROM budgets ORDER BY created_at ASC")
    suspend fun budgets(): List<BudgetEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY created_at ASC")
    suspend fun recurringRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_occurrences ORDER BY occurrence_date ASC")
    suspend fun recurringOccurrences(): List<RecurringOccurrenceEntity>

    @Query("SELECT * FROM exchange_rate_snapshots ORDER BY base_code ASC")
    suspend fun rateSnapshots(): List<ExchangeRateSnapshotEntity>

    @Query("SELECT * FROM exchange_rate_history ORDER BY fetched_at ASC")
    suspend fun rateHistory(): List<ExchangeRateHistoryEntity>

    @Query("SELECT * FROM pending_drafts ORDER BY created_at ASC")
    suspend fun pendingDrafts(): List<PendingDraftEntity>

    @Query("DELETE FROM recurring_occurrences")
    suspend fun deleteOccurrences()

    @Query("DELETE FROM pending_drafts")
    suspend fun deleteDrafts()

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteRules()

    @Query("DELETE FROM budgets")
    suspend fun deleteBudgets()

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Query("DELETE FROM accounts")
    suspend fun deleteAccounts()

    @Query("DELETE FROM exchange_rate_history")
    suspend fun deleteRateHistory()

    @Query("DELETE FROM exchange_rate_snapshots")
    suspend fun deleteRateSnapshots()

    @Query("DELETE FROM ledgers")
    suspend fun deleteLedgers()

    @Insert
    suspend fun insertLedgers(rows: List<LedgerEntity>)

    @Insert
    suspend fun insertAccounts(rows: List<AccountEntity>)

    @Insert
    suspend fun insertCategories(rows: List<CategoryEntity>)

    @Insert
    suspend fun insertTransactions(rows: List<TransactionEntity>)

    @Insert
    suspend fun insertBudgets(rows: List<BudgetEntity>)

    @Insert
    suspend fun insertRules(rows: List<RecurringRuleEntity>)

    @Insert
    suspend fun insertOccurrences(rows: List<RecurringOccurrenceEntity>)

    @Insert
    suspend fun insertRateSnapshots(rows: List<ExchangeRateSnapshotEntity>)

    @Insert
    suspend fun insertRateHistory(rows: List<ExchangeRateHistoryEntity>)

    @Insert
    suspend fun insertDrafts(rows: List<PendingDraftEntity>)
}
