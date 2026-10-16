package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.MonthlyTotal
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.StatisticsRepository
import com.example.localledger.domain.time.DateRangeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    private val databaseProvider: WalletDatabaseSource,
    private val dateRangeResolver: DateRangeResolver,
) : StatisticsRepository {
    override fun observeSummary(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<IncomeExpenseSummary> = flow {
        emitAll(databaseProvider.get().transactionDao().observeSummary(
            ledgerId, range.startInclusive, range.endExclusive,
        ).map { it.toDomain() })
    }

    override fun observeExpenseCategories(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<List<CategoryTotal>> = categories(ledgerId, range, income = false)

    override fun observeIncomeCategories(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<List<CategoryTotal>> = categories(ledgerId, range, income = true)

    private fun categories(
        ledgerId: String,
        range: DateTimeRange,
        income: Boolean,
    ): Flow<List<CategoryTotal>> = flow {
        val dao = databaseProvider.get().transactionDao()
        val source = if (income) {
            dao.observeIncomeCategories(ledgerId, range.startInclusive, range.endExclusive)
        } else {
            dao.observeExpenseCategories(ledgerId, range.startInclusive, range.endExclusive)
        }
        emitAll(source.map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeDailyTrend(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<List<DailyTotal>> = flow {
        emitAll(databaseProvider.get().transactionDao().observeTrendSource(
            ledgerId, range.startInclusive, range.endExclusive,
        ).map { rows ->
            val zone = dateRangeResolver.zoneId()
            rows.groupBy { Instant.ofEpochMilli(it.transactionAt).atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (date, values) -> values.toDailyTotal(date) }
        })
    }

    override fun observeMonthlyTrend(ledgerId: String, year: Int): Flow<List<MonthlyTotal>> = flow {
        val range = dateRangeResolver.year(year)
        emitAll(databaseProvider.get().transactionDao().observeTrendSource(
            ledgerId, range.startInclusive, range.endExclusive,
        ).map { rows ->
            val zone = dateRangeResolver.zoneId()
            val grouped = rows.groupBy {
                YearMonth.from(Instant.ofEpochMilli(it.transactionAt).atZone(zone))
            }
            (1..12).map { month ->
                val yearMonth = YearMonth.of(year, month)
                grouped[yearMonth].orEmpty().toMonthlyTotal(yearMonth)
            }
        })
    }

    private fun List<com.example.localledger.data.local.model.TrendSourceRow>.toDailyTotal(
        date: LocalDate,
    ): DailyTotal {
        val (income, expense) = totals()
        return DailyTotal(date, income, expense)
    }

    private fun List<com.example.localledger.data.local.model.TrendSourceRow>.toMonthlyTotal(
        yearMonth: YearMonth,
    ): MonthlyTotal {
        val (income, expense) = totals()
        return MonthlyTotal(yearMonth, income, expense)
    }

    private fun List<com.example.localledger.data.local.model.TrendSourceRow>.totals(): Pair<Long, Long> {
        var income = 0L
        var expense = 0L
        try {
            forEach { row ->
                when (row.type) {
                    TransactionType.INCOME -> income = Math.addExact(income, row.amountMinor)
                    TransactionType.EXPENSE -> expense = Math.addExact(expense, row.amountMinor)
                    TransactionType.TRANSFER -> Unit
                }
            }
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }
        return income to expense
    }
}

