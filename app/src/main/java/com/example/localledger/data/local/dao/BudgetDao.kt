package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.model.BudgetRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query(
        """
        SELECT b.*, c.name AS category_name
        FROM budgets b
        LEFT JOIN categories c ON c.id = b.category_id
        WHERE b.ledger_id = :ledgerId AND b.year_month = :yearMonth
        ORDER BY b.category_id IS NOT NULL, c.sort_order, b.created_at
        """,
    )
    fun observeMonth(ledgerId: String, yearMonth: String): Flow<List<BudgetRow>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE ledger_id = :ledgerId AND year_month = :yearMonth
          AND category_id IS :categoryId
        """,
    )
    suspend fun findBySlot(ledgerId: String, yearMonth: String, categoryId: String?): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: String): BudgetEntity?

    @Upsert
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id AND ledger_id = :ledgerId")
    suspend fun delete(id: String, ledgerId: String): Int
}
