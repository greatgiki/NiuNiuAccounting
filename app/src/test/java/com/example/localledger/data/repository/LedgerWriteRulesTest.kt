package com.example.localledger.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.usecase.DeleteTransactionUseCase
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import com.example.localledger.domain.usecase.SaveTransferUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LedgerWriteRulesTest {
    private lateinit var database: WalletDatabase
    private lateinit var ledgerId: String
    private lateinit var otherLedgerId: String
    private lateinit var cashAccountId: String
    private lateinit var bankAccountId: String
    private lateinit var expenseCategoryId: String
    private lateinit var incomeCategoryId: String
    private lateinit var transactions: TransactionRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var saveIncomeExpense: SaveIncomeExpenseUseCase
    private lateinit var saveTransfer: SaveTransferUseCase
    private lateinit var deleteTransaction: DeleteTransactionUseCase

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
            .build()
        val source = WalletDatabaseSource { database }
        val clock = Clock.systemUTC()
        val ledgers = LedgerRepositoryImpl(source, clock)
        accounts = AccountRepositoryImpl(source, clock)
        categories = CategoryRepositoryImpl(source, clock)
        transactions = TransactionRepositoryImpl(source, UnusedExchangeRates, clock)
        saveIncomeExpense = SaveIncomeExpenseUseCase(transactions)
        saveTransfer = SaveTransferUseCase(transactions)
        deleteTransaction = DeleteTransactionUseCase(transactions)

        ledgerId = ledgers.createLedger("日常")
        otherLedgerId = ledgers.createLedger("旅行")
        val ledgerAccounts = accounts.observeAccounts(ledgerId).first()
        cashAccountId = ledgerAccounts.first { it.name == "现金" }.id
        bankAccountId = ledgerAccounts.first { it.name == "银行卡" }.id
        expenseCategoryId = categories.observeCategories(ledgerId, CategoryType.EXPENSE)
            .first().first { it.name == "餐饮" }.id
        incomeCategoryId = categories.observeCategories(ledgerId, CategoryType.INCOME)
            .first().first { it.name == "工资" }.id
    }

    @After
    fun close() = database.close()

    @Test
    fun rejectsZeroAndNegativeAmounts() = runTest {
        assertTrue(save(amount = 0) is LedgerException.InvalidAmount)
        assertTrue(save(amount = -1) is LedgerException.InvalidAmount)
    }

    @Test
    fun rejectsCategoryTypeMismatch() = runTest {
        val error = save(categoryId = incomeCategoryId)
        assertTrue(error is LedgerException.CategoryTypeMismatch)
    }

    @Test
    fun rejectsTransferBetweenTheSameAccount() = runTest {
        val error = runCatching {
            saveTransfer(
                SaveTransactionCommand(
                    ledgerId = ledgerId,
                    type = TransactionType.TRANSFER,
                    amountMinor = 100,
                    accountId = cashAccountId,
                    toAccountId = cashAccountId,
                    transactionAt = 1_700_000_000_000L,
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is LedgerException.SameTransferAccount)
    }

    @Test
    fun rejectsArchivedAccountAndCategory() = runTest {
        accounts.archiveAccount(cashAccountId)
        assertTrue(save() is LedgerException.AccountArchived)

        val freshCash = accounts.observeAccounts(ledgerId).first().first { !it.isArchived }
        categories.archiveCategory(expenseCategoryId)
        val error = save(accountId = freshCash.id)
        assertTrue(error is LedgerException.CategoryArchived)
    }

    @Test
    fun deleteFromAnotherLedgerIsNotFoundAndKeepsTheRow() = runTest {
        val id = saveIncomeExpense(expenseCommand())
        val error = runCatching { deleteTransaction(otherLedgerId, id) }.exceptionOrNull()
        assertTrue(error is LedgerException.TransactionNotFound)
        assertNotNull(database.transactionDao().findById(id))

        deleteTransaction(ledgerId, id)
        assertEquals(null, database.transactionDao().findById(id))
    }

    @Test
    fun ledgerIdTriggerMapsToDomainErrorAndKeepsOwnership() = runTest {
        val account = database.accountDao().findById(cashAccountId)!!
        val category = database.categoryDao().findById(expenseCategoryId)!!

        val accountError = runCatching {
            writeOperation {
                database.accountDao().upsert(account.copy(ledgerId = otherLedgerId))
            }
        }.exceptionOrNull()
        val categoryError = runCatching {
            writeOperation {
                database.categoryDao().upsert(category.copy(ledgerId = otherLedgerId))
            }
        }.exceptionOrNull()

        assertTrue(accountError is LedgerException.AccountLedgerMismatch)
        assertTrue(categoryError is LedgerException.CategoryLedgerMismatch)
        assertEquals(ledgerId, database.accountDao().findById(cashAccountId)!!.ledgerId)
        assertEquals(ledgerId, database.categoryDao().findById(expenseCategoryId)!!.ledgerId)
    }

    @Test
    fun writeOperationRethrowsCancellation() = runTest {
        val error = runCatching {
            writeOperation<Unit> { throw CancellationException("stop") }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    private suspend fun save(
        amount: Long = 100,
        accountId: String = cashAccountId,
        categoryId: String = expenseCategoryId,
    ): Throwable? = runCatching {
        saveIncomeExpense(expenseCommand(amount, accountId, categoryId))
    }.exceptionOrNull()

    private fun expenseCommand(
        amount: Long = 100,
        accountId: String = cashAccountId,
        categoryId: String = expenseCategoryId,
    ) = SaveTransactionCommand(
        ledgerId = ledgerId,
        type = TransactionType.EXPENSE,
        amountMinor = amount,
        accountId = accountId,
        categoryId = categoryId,
        transactionAt = 1_700_000_000_000L,
    )
}

private object UnusedExchangeRates : ExchangeRateRepository {
    override fun observe(baseCode: String) = kotlinx.coroutines.flow.flowOf<ExchangeRateSnapshot?>(null)
    override suspend fun get(baseCode: String): ExchangeRateSnapshot? = null
    override suspend fun save(snapshot: ExchangeRateSnapshot) = Unit
    override suspend fun baseCodesInUse(): List<String> = emptyList()
}
