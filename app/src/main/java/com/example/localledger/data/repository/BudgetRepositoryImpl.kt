package com.example.localledger.data.repository

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.data.mapper.toEntity
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val clock: Clock,
) : BudgetRepository {
    override fun observeMonth(ledgerId: String, yearMonth: YearMonth): Flow<List<Budget>> = flow {
        emitAll(
            databaseProvider.get().budgetDao().observeMonth(ledgerId, yearMonth.toString())
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override suspend fun save(budget: Budget) = writeOperation {
        if (budget.amountMinor <= 0L) throw LedgerException.InvalidBudget()
        val database = databaseProvider.get()
        database.withTransaction {
            val ledger = database.ledgerDao().findById(budget.ledgerId) ?: throw LedgerException.LedgerNotFound()
            if (ledger.isArchived) throw LedgerException.LedgerArchived()
            val categoryId = budget.categoryId
            if (categoryId != null) {
                val category = database.categoryDao().findById(categoryId) ?: throw LedgerException.CategoryNotFound()
                if (category.ledgerId != budget.ledgerId) throw LedgerException.CategoryLedgerMismatch()
                if (category.type != CategoryType.EXPENSE) throw LedgerException.CategoryTypeMismatch()
            }
            val slot = database.budgetDao().findBySlot(budget.ledgerId, budget.yearMonth.toString(), categoryId)
            if (slot != null && slot.id != budget.id) throw LedgerException.DuplicateBudget()
            val existing = database.budgetDao().findById(budget.id)
            if (existing != null && existing.ledgerId != budget.ledgerId) throw LedgerException.BudgetNotFound()
            val now = clock.millis()
            database.budgetDao().upsert(
                budget.copy(
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ).toEntity(),
            )
        }
        Unit
    }

    override suspend fun delete(ledgerId: String, budgetId: String) = writeOperation {
        val database = databaseProvider.get()
        database.withTransaction {
            if (database.budgetDao().delete(budgetId, ledgerId) != 1) throw LedgerException.BudgetNotFound()
        }
        Unit
    }
}
