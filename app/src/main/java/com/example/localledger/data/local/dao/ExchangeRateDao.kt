package com.example.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rate_snapshots WHERE base_code = :baseCode")
    fun observe(baseCode: String): Flow<ExchangeRateSnapshotEntity?>

    @Query("SELECT * FROM exchange_rate_snapshots WHERE base_code = :baseCode")
    suspend fun find(baseCode: String): ExchangeRateSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: ExchangeRateSnapshotEntity)

    @Query("SELECT DISTINCT currency_code FROM ledgers WHERE is_archived = 0")
    suspend fun ledgerBaseCodes(): List<String>
}