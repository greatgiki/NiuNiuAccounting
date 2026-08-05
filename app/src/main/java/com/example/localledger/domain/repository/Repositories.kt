package com.example.localledger.domain.repository

import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.model.MonthlyTotal
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeLedgers(): Flow<List<Ledger>>
    suspend fun createLedger(name: String): String
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
    suspend fun deleteTransaction(transactionId: String)
}

interface StatisticsRepository {
    fun observeSummary(ledgerId: String, range: DateTimeRange): Flow<IncomeExpenseSummary>
    fun observeExpenseCategories(ledgerId: String, range: DateTimeRange): Flow<List<CategoryTotal>>
    fun observeDailyTrend(ledgerId: String, range: DateTimeRange): Flow<List<DailyTotal>>
    fun observeMonthlyTrend(ledgerId: String, year: Int): Flow<List<MonthlyTotal>>
}
