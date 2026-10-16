package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.data.mapper.toHistoryEntity
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
) : ExchangeRateRepository {
    override fun observe(baseCode: String): Flow<ExchangeRateSnapshot?> = flow {
        emitAll(
            databaseProvider.get().exchangeRateDao().observe(baseCode).map { it?.toDomain() },
        )
    }

    override suspend fun get(baseCode: String): ExchangeRateSnapshot? =
        databaseProvider.get().exchangeRateDao().find(baseCode)?.toDomain()

    override suspend fun save(snapshot: ExchangeRateSnapshot) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            database.exchangeRateDao().upsert(snapshot.toEntity())
            database.exchangeRateDao().insertHistory(snapshot.toHistoryEntity())
        }
        Unit
    }

    override suspend fun baseCodesInUse(): List<String> =
        databaseProvider.get().exchangeRateDao().ledgerBaseCodes()
}
