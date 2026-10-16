package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.money.CurrencyConverter
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val exchangeRates: ExchangeRateRepository,
    private val clock: Clock,
) : TransactionRepository {
    override fun observeTransaction(transactionId: String): Flow<TransactionDetail?> = flow {
        emitAll(
            databaseProvider.get().transactionDao().observeDetailById(transactionId)
                .map { row -> row?.toDomain() },
        )
    }

    override fun observeTransactions(
        ledgerId: String,
        filter: TransactionFilter,
    ): Flow<List<TransactionDetail>> = flow {
        val range = filter.range
        val limit = filter.limit ?: Int.MAX_VALUE
        require(limit > 0) { "limit must be positive" }
        emitAll(
            databaseProvider.get().transactionDao().observeDetails(
                ledgerId = ledgerId,
                type = filter.type,
                categoryId = filter.categoryId,
                accountId = filter.accountId,
                startInclusive = range?.startInclusive ?: Long.MIN_VALUE,
                endExclusive = range?.endExclusive ?: Long.MAX_VALUE,
                keyword = filter.keyword.trim(),
                limit = limit,
            ).map { rows -> rows.map { it.toDomain() } },
        )
    }

    override suspend fun saveTransaction(command: SaveTransactionCommand): String = writeOperation {
        validateShape(command)
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(command.ledgerId)
                ?: throw LedgerException.LedgerNotFound()
            if (ledger.isArchived) throw LedgerException.LedgerArchived()

            val source = database.accountDao().findById(command.accountId)
                ?: throw LedgerException.AccountNotFound()
            validateAccount(source.ledgerId, source.isArchived, command.ledgerId)

            val destination = command.toAccountId?.let { id ->
                database.accountDao().findById(id) ?: throw LedgerException.AccountNotFound()
            }
            destination?.let { validateAccount(it.ledgerId, it.isArchived, command.ledgerId) }

            val category = command.categoryId?.let { id ->
                database.categoryDao().findById(id) ?: throw LedgerException.CategoryNotFound()
            }
            category?.let {
                if (it.ledgerId != command.ledgerId) throw LedgerException.CategoryLedgerMismatch()
                if (it.isArchived) throw LedgerException.CategoryArchived()
                val requiredType = CategoryType.valueOf(command.type.name)
                if (it.type != requiredType) throw LedgerException.CategoryTypeMismatch()
            }

            val requestedId = command.transactionId
            val existing = requestedId?.let { database.transactionDao().findById(it) }
            if (existing != null && existing.ledgerId != command.ledgerId) {
                throw LedgerException.TransactionLedgerMismatch()
            }

            val sourceCurrency = CurrencyCatalog.of(source.currencyCode).code
            val ledgerCurrency = CurrencyCatalog.of(ledger.currencyCode).code
            val destinationCurrency = destination?.let { CurrencyCatalog.of(it.currencyCode).code }
            val needsRate = sourceCurrency != ledgerCurrency ||
                (destinationCurrency != null && destinationCurrency != sourceCurrency && command.toAmountMinor == null)
            val snapshot = if (needsRate) exchangeRates.get(ledgerCurrency) else null
            val baseAmount = CurrencyConverter.toBaseMinor(
                command.amountMinor,
                sourceCurrency,
                ledgerCurrency,
                snapshot,
            )
            if (baseAmount <= 0L) throw LedgerException.InvalidAmount()
            val toAmount = resolveToAmount(command, sourceCurrency, destinationCurrency, ledgerCurrency, snapshot)
            val fxRate = CurrencyConverter.formatRate(
                CurrencyConverter.basePerAccountUnit(sourceCurrency, ledgerCurrency, snapshot),
            )

            val id = existing?.id ?: requestedId ?: UUID.randomUUID().toString()
            val now = clock.millis()
            database.transactionDao().upsert(
                TransactionEntity(
                    id = id,
                    ledgerId = command.ledgerId,
                    type = command.type,
                    amountMinor = command.amountMinor,
                    accountId = command.accountId,
                    toAccountId = command.toAccountId,
                    categoryId = command.categoryId,
                    transactionAt = command.transactionAt,
                    note = command.note?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    toAmountMinor = toAmount,
                    baseAmountMinor = baseAmount,
                    fxRate = fxRate,
                ),
            )
            id
        }
    }

    override suspend fun contains(transactionId: String): Boolean =
        databaseProvider.get().transactionDao().findById(transactionId) != null

    override suspend fun deleteTransaction(ledgerId: String, transactionId: String) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            database.transactionDao().findByIdInLedger(transactionId, ledgerId)
                ?: throw LedgerException.TransactionNotFound()
            if (database.transactionDao().deleteById(transactionId, ledgerId) != 1) {
                throw LedgerException.TransactionNotFound()
            }
        }
    }

    private fun validateShape(command: SaveTransactionCommand) {
        if (command.amountMinor <= 0) throw LedgerException.InvalidAmount()
        when (command.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (command.categoryId == null || command.toAccountId != null) {
                    throw LedgerException.InvalidTransactionType()
                }
            }
            TransactionType.TRANSFER -> {
                if (command.toAccountId == null || command.categoryId != null || command.toAmountMinor != null && command.toAmountMinor <= 0) {
                    throw if (command.toAmountMinor != null && command.toAmountMinor <= 0) {
                        LedgerException.InvalidAmount()
                    } else {
                        LedgerException.InvalidTransactionType()
                    }
                }
                if (command.accountId == command.toAccountId) {
                    throw LedgerException.SameTransferAccount()
                }
            }
        }
        if (command.type != TransactionType.TRANSFER && command.toAmountMinor != null) {
            throw LedgerException.InvalidTransactionType()
        }
    }

    private fun resolveToAmount(
        command: SaveTransactionCommand,
        sourceCurrency: String,
        destinationCurrency: String?,
        ledgerCurrency: String,
        snapshot: com.example.localledger.domain.model.ExchangeRateSnapshot?,
    ): Long? {
        if (command.type != TransactionType.TRANSFER) return null
        command.toAmountMinor?.let { return it }
        val destination = destinationCurrency ?: throw LedgerException.InvalidTransactionType()
        return if (destination == sourceCurrency) {
            command.amountMinor
        } else {
            CurrencyConverter.betweenAccounts(
                command.amountMinor,
                sourceCurrency,
                destination,
                ledgerCurrency,
                snapshot,
            ).also { if (it <= 0L) throw LedgerException.InvalidAmount() }
        }
    }

    private fun validateAccount(accountLedgerId: String, archived: Boolean, ledgerId: String) {
        if (accountLedgerId != ledgerId) throw LedgerException.AccountLedgerMismatch()
        if (archived) throw LedgerException.AccountArchived()
    }
}

