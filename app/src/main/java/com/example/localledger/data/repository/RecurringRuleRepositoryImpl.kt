package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.ResumeChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.recurrence.RecurrenceSchedule
import com.example.localledger.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRuleRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val clock: Clock,
) : RecurringRuleRepository {
    override fun observe(ledgerId: String): Flow<List<RecurringRule>> = flow {
        emitAll(
            databaseProvider.get().recurringRuleDao().observe(ledgerId)
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override suspend fun listActive(): List<RecurringRule> =
        databaseProvider.get().recurringRuleDao().listActive().map { it.toDomain() }

    override suspend fun hasOccurrence(ruleId: String, occurrenceDate: LocalDate): Boolean =
        databaseProvider.get().recurringRuleDao().countOccurrence(ruleId, occurrenceDate.toString()) > 0

    override suspend fun recordOccurrence(ruleId: String, occurrenceDate: LocalDate, transactionId: String) {
        databaseProvider.get().recurringRuleDao().insertOccurrence(
            RecurringOccurrenceEntity(ruleId, occurrenceDate.toString(), transactionId),
        )
    }

    override suspend fun updateNextRun(ruleId: String, nextRunDate: LocalDate) {
        databaseProvider.get().recurringRuleDao().updateNextRun(ruleId, nextRunDate.toString(), clock.millis())
    }

    override suspend fun setPaused(ledgerId: String, ruleId: String, paused: Boolean) = writeOperation {
        val updated = databaseProvider.get().recurringRuleDao().setPaused(ruleId, ledgerId, paused, clock.millis())
        if (updated != 1) throw LedgerException.RecurringRuleNotFound()
    }

    override suspend fun resume(
        ledgerId: String,
        ruleId: String,
        choice: ResumeChoice,
        today: LocalDate,
    ) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            val existing = database.recurringRuleDao().findById(ruleId)
                ?: throw LedgerException.RecurringRuleNotFound()
            if (existing.ledgerId != ledgerId) throw LedgerException.RecurringRuleNotFound()
            val now = clock.millis()
            if (choice == ResumeChoice.SKIP_MISSED) {
                val next = RecurrenceSchedule.nextAfter(
                    LocalDate.parse(existing.startDate),
                    existing.period,
                    today,
                )
                database.recurringRuleDao().updateNextRun(ruleId, next.toString(), now)
            }
            if (database.recurringRuleDao().setPaused(ruleId, ledgerId, false, now) != 1) {
                throw LedgerException.RecurringRuleNotFound()
            }
        }
        Unit
    }

    override suspend fun delete(ledgerId: String, ruleId: String, deleteGenerated: Boolean) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            if (deleteGenerated) {
                database.recurringRuleDao().occurrenceTransactionIds(ruleId).forEach { transactionId ->
                    database.transactionDao().deleteById(transactionId, ledgerId)
                }
            }
            if (database.recurringRuleDao().delete(ruleId, ledgerId) != 1) {
                throw LedgerException.RecurringRuleNotFound()
            }
        }
        Unit
    }

    override suspend fun save(rule: RecurringRule) = writeOperation {
        validate(rule)
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(rule.ledgerId) ?: throw LedgerException.LedgerNotFound()
            if (ledger.isArchived) throw LedgerException.LedgerArchived()
            val source = database.accountDao().findById(rule.accountId) ?: throw LedgerException.AccountNotFound()
            if (source.ledgerId != rule.ledgerId) throw LedgerException.AccountLedgerMismatch()
            if (source.isArchived) throw LedgerException.AccountArchived()
            val destination = rule.toAccountId?.let { id ->
                database.accountDao().findById(id) ?: throw LedgerException.AccountNotFound()
            }
            destination?.let {
                if (it.ledgerId != rule.ledgerId) throw LedgerException.AccountLedgerMismatch()
                if (it.isArchived) throw LedgerException.AccountArchived()
            }
            val category = rule.categoryId?.let { id ->
                database.categoryDao().findById(id) ?: throw LedgerException.CategoryNotFound()
            }
            category?.let {
                if (it.ledgerId != rule.ledgerId) throw LedgerException.CategoryLedgerMismatch()
                if (it.isArchived) throw LedgerException.CategoryArchived()
                val required = CategoryType.valueOf(rule.type.name)
                if (it.type != required) throw LedgerException.CategoryTypeMismatch()
            }
            val existing = database.recurringRuleDao().findById(rule.id)
            if (existing != null && existing.ledgerId != rule.ledgerId) {
                throw LedgerException.RecurringRuleNotFound()
            }
            val now = clock.millis()
            val next = if (rule.nextRunDate.isBefore(rule.startDate)) rule.startDate else rule.nextRunDate
            database.recurringRuleDao().upsert(
                rule.copy(
                    nextRunDate = next,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ).toEntity(),
            )
        }
        Unit
    }

    private fun validate(rule: RecurringRule) {
        if (rule.amountMinor <= 0L) throw LedgerException.InvalidAmount()
        if (rule.toAmountMinor != null && rule.toAmountMinor <= 0L) throw LedgerException.InvalidAmount()
        if (rule.endDate != null && rule.endDate.isBefore(rule.startDate)) throw LedgerException.InvalidRecurrence()
        when (rule.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (rule.categoryId == null || rule.toAccountId != null) throw LedgerException.InvalidTransactionType()
            }
            TransactionType.TRANSFER -> {
                if (rule.toAccountId == null || rule.categoryId != null) throw LedgerException.InvalidTransactionType()
                if (rule.accountId == rule.toAccountId) throw LedgerException.SameTransferAccount()
            }
        }
    }
}
