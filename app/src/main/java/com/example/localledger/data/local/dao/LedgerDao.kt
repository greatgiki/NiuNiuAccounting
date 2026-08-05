package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.localledger.data.local.entity.LedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledgers ORDER BY is_archived ASC, created_at ASC")
    fun observeAll(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    suspend fun findById(id: String): LedgerEntity?

    @Query("SELECT * FROM ledgers WHERE is_archived = 0 ORDER BY created_at ASC LIMIT 1")
    suspend fun findFirstActive(): LedgerEntity?

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LedgerEntity)

    @Update
    suspend fun update(entity: LedgerEntity): Int

    @Query("UPDATE ledgers SET is_archived = 1 WHERE id = :id")
    suspend fun archive(id: String): Int
}

