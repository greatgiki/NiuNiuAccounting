package com.example.localledger.domain.repository

import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.model.MonthlyTotal
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface LedgerRepository {
    fun observeLedgers(): Flow<List<Ledger>>
    suspend fun createLedger(name: String, currencyCode: String = "CNY"): String
    suspend fun updateLedger(ledger: Ledger)
    suspend fun archiveLedger(ledgerId: String)
}

interface AccountRepository {
    fun observeAccounts(ledgerId: String): Flow<List<Account>>
    fun observeAccountBalances(ledgerId: String): Flow<List<AccountBalance>>
    suspend fun saveAccount(account: Account)
    suspend fun archiveAccount(accountId: String)
}

interface CategoryRepository {
    fun observeCategories(ledgerId: String, type: CategoryType): Flow<List<Category>>
    suspend fun saveCategory(category: Category)
    suspend fun archiveCategory(categoryId: String)
}

interface TransactionRepository {
    fun observeTransaction(transactionId: String): Flow<TransactionDetail?>

    fun observeTransactions(
        ledgerId: String,
        filter: TransactionFilter = TransactionFilter(),
    ): Flow<List<TransactionDetail>>

    suspend fun saveTransaction(command: SaveTransactionCommand): String
    suspend fun deleteTransaction(ledgerId: String, transactionId: String)
    suspend fun contains(transactionId: String): Boolean
}

interface StatisticsRepository {
    fun observeSummary(ledgerId: String, range: DateTimeRange): Flow<IncomeExpenseSummary>
    fun observeExpenseCategories(ledgerId: String, range: DateTimeRange): Flow<List<CategoryTotal>>
    fun observeIncomeCategories(ledgerId: String, range: DateTimeRange): Flow<List<CategoryTotal>>
    fun observeDailyTrend(ledgerId: String, range: DateTimeRange): Flow<List<DailyTotal>>
    fun observeMonthlyTrend(ledgerId: String, year: Int): Flow<List<MonthlyTotal>>
}

interface BudgetRepository {
    fun observeMonth(ledgerId: String, yearMonth: YearMonth): Flow<List<Budget>>
    suspend fun save(budget: Budget)
    suspend fun delete(ledgerId: String, budgetId: String)
}

interface RecurringRuleRepository {
    fun observe(ledgerId: String): Flow<List<RecurringRule>>
    suspend fun save(rule: RecurringRule)
    suspend fun setPaused(ledgerId: String, ruleId: String, paused: Boolean)
    suspend fun delete(ledgerId: String, ruleId: String)
    suspend fun listActive(): List<RecurringRule>
    suspend fun hasOccurrence(ruleId: String, occurrenceDate: java.time.LocalDate): Boolean
    suspend fun recordOccurrence(ruleId: String, occurrenceDate: java.time.LocalDate, transactionId: String)
    suspend fun updateNextRun(ruleId: String, nextRunDate: java.time.LocalDate)
}

interface ExchangeRateRepository {
    fun observe(baseCode: String): Flow<ExchangeRateSnapshot?>
    suspend fun get(baseCode: String): ExchangeRateSnapshot?
    suspend fun save(snapshot: ExchangeRateSnapshot)
    suspend fun baseCodesInUse(): List<String>
}

interface ExchangeRateRemote {
    suspend fun fetch(baseCode: String): ExchangeRateSnapshot
}
