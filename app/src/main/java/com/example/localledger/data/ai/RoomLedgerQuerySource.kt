package com.example.localledger.data.ai

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.domain.ai.LedgerQuerySource
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.BudgetRepository
import com.example.localledger.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLedgerQuerySource @Inject constructor(
    private val database: WalletDatabaseSource,
    private val statistics: StatisticsRepository,
    private val budgets: BudgetRepository,
) : LedgerQuerySource {
    override suspend fun summary(ledgerId: String, range: DateTimeRange): IncomeExpenseSummary =
        statistics.observeSummary(ledgerId, range).first()

    override suspend fun categories(
        ledgerId: String,
        range: DateTimeRange,
        income: Boolean,
    ): List<CategoryTotal> = if (income) {
        statistics.observeIncomeCategories(ledgerId, range).first()
    } else {
        statistics.observeExpenseCategories(ledgerId, range).first()
    }

    override suspend fun topTransactions(
        ledgerId: String,
        range: DateTimeRange,
        type: TransactionType?,
        limit: Int,
    ): List<TransactionDetail> = database.get().transactionDao().topByBaseAmount(
        ledgerId = ledgerId,
        startInclusive = range.startInclusive,
        endExclusive = range.endExclusive,
        type = type,
        limit = limit.coerceIn(1, 8),
    ).map { it.toDomain() }

    override suspend fun budgets(ledgerId: String, month: YearMonth): List<Budget> =
        budgets.observeMonth(ledgerId, month).first()
}
