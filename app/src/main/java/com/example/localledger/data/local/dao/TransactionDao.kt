package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.data.local.model.CategoryTotalRow
import com.example.localledger.data.local.model.ExternalKeyRow
import com.example.localledger.data.local.model.FuzzyTransactionRow
import com.example.localledger.data.local.model.IncomeExpenseSummaryRow
import com.example.localledger.data.local.model.TransactionDetailRow
import com.example.localledger.data.local.model.TrendSourceRow
import com.example.localledger.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: String): TransactionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE account_id = :accountId OR to_account_id = :accountId
        """,
    )
    suspend fun countUsingAccount(accountId: String): Int

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun findByIdInLedger(id: String, ledgerId: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun deleteById(id: String, ledgerId: String): Int

    @Query("SELECT * FROM transactions WHERE ledger_id = :ledgerId ORDER BY transaction_at ASC, created_at ASC")
    suspend fun listByLedger(ledgerId: String): List<TransactionEntity>

    @Query(
        """
        SELECT external_source, external_id
        FROM transactions
        WHERE ledger_id = :ledgerId AND external_id IS NOT NULL AND external_source IS NOT NULL
        """,
    )
    suspend fun listExternalKeys(ledgerId: String): List<ExternalKeyRow>

    @Query(
        """
        SELECT id, type, amount_minor, transaction_at, origin, external_source, external_id
        FROM transactions
        WHERE ledger_id = :ledgerId
          AND transaction_at >= :startInclusive
          AND transaction_at < :endExclusive
        """,
    )
    suspend fun listFuzzyCandidates(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<FuzzyTransactionRow>

    @Query(
        """
        UPDATE transactions
        SET external_source = :source, external_id = :externalId, updated_at = :updatedAt
        WHERE id = :id AND ledger_id = :ledgerId AND external_id IS NULL
        """,
    )
    suspend fun attachExternalId(
        id: String,
        ledgerId: String,
        source: String,
        externalId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE transactions
        SET base_amount_minor = :baseAmountMinor, fx_rate = :fxRate, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateValuation(id: String, baseAmountMinor: Long, fxRate: String, updatedAt: Long): Int

    @Query(
        """
        SELECT t.*,
            source.name AS account_name,
            destination.name AS to_account_name,
            c.name AS category_name,
            c.icon_key AS category_icon_key,
            source.currency_code AS account_currency_code,
            destination.currency_code AS to_account_currency_code
        FROM transactions t
        JOIN accounts source ON source.id = t.account_id
        LEFT JOIN accounts destination ON destination.id = t.to_account_id
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.id = :id
        """,
    )
    fun observeDetailById(id: String): Flow<TransactionDetailRow?>

    @Query(
        """
        SELECT t.*,
            source.name AS account_name,
            destination.name AS to_account_name,
            c.name AS category_name,
            c.icon_key AS category_icon_key,
            source.currency_code AS account_currency_code,
            destination.currency_code AS to_account_currency_code
        FROM transactions t
        JOIN accounts source ON source.id = t.account_id
        LEFT JOIN accounts destination ON destination.id = t.to_account_id
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.ledger_id = :ledgerId
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:accountId IS NULL OR t.account_id = :accountId OR t.to_account_id = :accountId)
          AND t.transaction_at >= :startInclusive
          AND t.transaction_at < :endExclusive
          AND instr(lower(COALESCE(t.note, '')), lower(:keyword)) > 0
        ORDER BY t.transaction_at DESC, t.created_at DESC
        LIMIT :limit
        """,
    )
    fun observeDetails(
        ledgerId: String,
        type: TransactionType?,
        categoryId: String?,
        accountId: String?,
        startInclusive: Long,
        endExclusive: Long,
        keyword: String,
        limit: Int,
    ): Flow<List<TransactionDetailRow>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN base_amount_minor ELSE 0 END), 0) AS income_minor,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN base_amount_minor ELSE 0 END), 0) AS expense_minor
        FROM transactions
        WHERE ledger_id = :ledgerId
          AND transaction_at >= :startInclusive
          AND transaction_at < :endExclusive
        """,
    )
    fun observeSummary(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<IncomeExpenseSummaryRow>

    @Query(
        """
        SELECT
            c.id AS category_id,
            c.name AS category_name,
            c.icon_key AS icon_key,
            SUM(t.base_amount_minor) AS amount_minor
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        WHERE t.ledger_id = :ledgerId
          AND t.type = 'EXPENSE'
          AND t.transaction_at >= :startInclusive
          AND t.transaction_at < :endExclusive
        GROUP BY c.id, c.name, c.icon_key
        ORDER BY amount_minor DESC
        """,
    )
    fun observeExpenseCategories(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT
            c.id AS category_id,
            c.name AS category_name,
            c.icon_key AS icon_key,
            SUM(t.base_amount_minor) AS amount_minor
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        WHERE t.ledger_id = :ledgerId
          AND t.type = 'INCOME'
          AND t.transaction_at >= :startInclusive
          AND t.transaction_at < :endExclusive
        GROUP BY c.id, c.name, c.icon_key
        ORDER BY amount_minor DESC
        """,
    )
    fun observeIncomeCategories(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT transaction_at, type, base_amount_minor AS amount_minor
        FROM transactions
        WHERE ledger_id = :ledgerId
          AND type IN ('INCOME', 'EXPENSE')
          AND transaction_at >= :startInclusive
          AND transaction_at < :endExclusive
        ORDER BY transaction_at ASC
        """,
    )
    fun observeTrendSource(
        ledgerId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<TrendSourceRow>>
}

