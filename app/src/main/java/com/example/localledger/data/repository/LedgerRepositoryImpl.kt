package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.database.PresetData
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.money.RevalueMode
import com.example.localledger.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val clock: Clock,
) : LedgerRepository {
    override fun observeLedgers(): Flow<List<Ledger>> = flow {
        emitAll(databaseProvider.get().ledgerDao().observeAll().map { list -> list.map { it.toDomain() } })
    }

    override suspend fun createLedger(name: String, currencyCode: String): String = writeOperation {
        val normalizedName = name.trim().ifEmpty { throw LedgerException.InvalidName() }
        val code = CurrencyCatalog.of(currencyCode).code
        val database = databaseProvider.get()
        val id = UUID.randomUUID().toString()
        val now = clock.millis()
        database.withTransaction {
            database.ledgerDao().insert(PresetData.ledger(id, normalizedName, now).copy(currencyCode = code))
            PresetData.accounts(id, now).forEach { database.accountDao().upsert(it) }
            PresetData.categories(id, now).forEach { database.categoryDao().upsert(it) }
        }
        id
    }

    override suspend fun updateLedger(ledger: Ledger) = writeOperation {
        val name = ledger.name.trim().ifEmpty { throw LedgerException.InvalidName() }
        val code = CurrencyCatalog.of(ledger.currencyCode).code
        val database = databaseProvider.get()
        database.withTransaction {
            val existing = database.ledgerDao().findById(ledger.id)
                ?: throw LedgerException.LedgerNotFound()
            database.ledgerDao().update(
                ledger.copy(
                    name = name,
                    currencyCode = code,
                    createdAt = existing.createdAt,
                ).toEntity(),
            )
            if (existing.currencyCode != code) {
                LedgerRevaluator.apply(database, ledger.id, code, RevalueMode.AS_OF_TRANSACTION, clock.millis())
            }
        }
        Unit
    }

    override suspend fun revalueWithCurrentRates(ledgerId: String): Int = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(ledgerId) ?: throw LedgerException.LedgerNotFound()
            LedgerRevaluator.apply(
                database,
                ledgerId,
                ledger.currencyCode,
                RevalueMode.CURRENT,
                clock.millis(),
            )
        }
    }

    override suspend fun archiveLedger(ledgerId: String) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            database.ledgerDao().findById(ledgerId) ?: throw LedgerException.LedgerNotFound()
            database.ledgerDao().archive(ledgerId)
        }
        Unit
    }
}

