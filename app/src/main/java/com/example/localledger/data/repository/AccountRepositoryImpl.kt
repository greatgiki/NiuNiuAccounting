package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val databaseProvider: DatabaseProvider,
    private val clock: Clock,
) : AccountRepository {
    override fun observeAccounts(ledgerId: String): Flow<List<Account>> = flow {
        emitAll(databaseProvider.get().accountDao().observeByLedger(ledgerId).map { rows ->
            rows.map { it.toDomain() }
        })
    }

    override fun observeAccountBalances(ledgerId: String): Flow<List<AccountBalance>> = flow {
        emitAll(databaseProvider.get().accountDao().observeBalances(ledgerId).map { rows ->
            rows.map { it.toDomain() }
        })
    }

    override suspend fun saveAccount(account: Account) = writeOperation {
        val normalizedName = account.name.trim().ifEmpty { throw LedgerException.InvalidName() }
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(account.ledgerId)
                ?: throw LedgerException.LedgerNotFound()
            if (ledger.isArchived) throw LedgerException.LedgerArchived()
            val existing = database.accountDao().findById(account.id)
            if (existing != null && existing.ledgerId != account.ledgerId) {
                throw LedgerException.AccountLedgerMismatch()
            }
            val now = clock.millis()
            database.accountDao().upsert(
                account.copy(
                    name = normalizedName,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ).toEntity(),
            )
        }
    }

    override suspend fun archiveAccount(accountId: String) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            database.accountDao().findById(accountId) ?: throw LedgerException.AccountNotFound()
            database.accountDao().archive(accountId, clock.millis())
        }
        Unit
    }
}

