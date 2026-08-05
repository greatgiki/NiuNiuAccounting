package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.model.AccountBalanceRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query(
        """
        SELECT * FROM accounts
        WHERE ledger_id = :ledgerId
        ORDER BY is_archived ASC, sort_order ASC, created_at ASC
        """,
    )
    fun observeByLedger(ledgerId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(entity: AccountEntity)

    @Query("UPDATE accounts SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long): Int

    @Query(
        """
        SELECT a.*,
            a.initial_balance_minor + COALESCE(SUM(
                CASE
                    WHEN t.type = 'INCOME' AND t.account_id = a.id THEN t.amount_minor
                    WHEN t.type = 'EXPENSE' AND t.account_id = a.id THEN -t.amount_minor
                    WHEN t.type = 'TRANSFER' AND t.account_id = a.id THEN -t.amount_minor
                    WHEN t.type = 'TRANSFER' AND t.to_account_id = a.id THEN t.amount_minor
                    ELSE 0
                END
            ), 0) AS balance_minor
        FROM accounts a
        LEFT JOIN transactions t
            ON t.ledger_id = a.ledger_id
            AND (t.account_id = a.id OR t.to_account_id = a.id)
        WHERE a.ledger_id = :ledgerId
        GROUP BY a.id
        ORDER BY a.is_archived ASC, a.sort_order ASC, a.created_at ASC
        """,
    )
    fun observeBalances(ledgerId: String): Flow<List<AccountBalanceRow>>
}

