package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE ledger_id = :ledgerId AND type = :type
        ORDER BY is_archived ASC, sort_order ASC, created_at ASC
        """,
    )
    fun observeByLedgerAndType(ledgerId: String, type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Query("UPDATE categories SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long): Int
}

