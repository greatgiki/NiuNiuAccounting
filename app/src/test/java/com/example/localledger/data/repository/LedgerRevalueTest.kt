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
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import com.example.localledger.domain.usecase.SaveTransferUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LedgerRevalueTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WalletDatabase
    private lateinit var ledgers: LedgerRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var rates: ExchangeRateRepositoryImpl
    private lateinit var ledgerId: String
    private lateinit var cashId: String
    private lateinit var bankId: String
    private lateinit var foodId: String
    private lateinit var saveIncomeExpense: SaveIncomeExpenseUseCase
    private lateinit var saveTransfer: SaveTransferUseCase

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
            .build()
        val source = WalletDatabaseSource { database }
        ledgers = LedgerRepositoryImpl(source, clock)
        accounts = AccountRepositoryImpl(source, clock)
        val categories = CategoryRepositoryImpl(source, clock)
        rates = ExchangeRateRepositoryImpl(source)
        val transactions = TransactionRepositoryImpl(source, rates, clock)
        saveIncomeExpense = SaveIncomeExpenseUseCase(transactions)
        saveTransfer = SaveTransferUseCase(transactions)

        ledgerId = ledgers.createLedger("日常")
        val ledgerAccounts = accounts.observeAccounts(ledgerId).first()
        val cash = ledgerAccounts.first { it.name == "现金" }
        cashId = cash.id
        bankId = ledgerAccounts.first { it.name == "银行卡" }.id
        foodId = categories.observeCategories(ledgerId, CategoryType.EXPENSE).first().first { it.name == "餐饮" }.id
        accounts.saveAccount(cash.copy(currencyCode = "USD"))
        rates.save(ExchangeRateSnapshot("CNY", mapOf("USD" to BigDecimal("0.14")), 500L, "test"))
    }

    @After
    fun close() = database.close()

    @Test
    fun changingBaseCurrencyUsesAsOfRatesAndRollsBackWhenARateIsMissing() = runTest {
        val usdExpense = saveIncomeExpense(expense(100, cashId, 2_000L))
        val dated = saveIncomeExpense(expense(700, bankId, 2_000L))
        val earlier = saveIncomeExpense(expense(700, bankId, 100L))
        val transfer = saveTransfer(
            SaveTransactionCommand(
                ledgerId = ledgerId,
                type = TransactionType.TRANSFER,
                amountMinor = 100,
                accountId = cashId,
                toAccountId = bankId,
                transactionAt = 2_000L,
                toAmountMinor = 700,
            ),
        )

        assertEquals(714L, database.transactionDao().findById(usdExpense)!!.baseAmountMinor)

        val ledger = ledgers.observeLedgers().first().first { it.id == ledgerId }
        try {
            ledgers.updateLedger(ledger.copy(currencyCode = "USD"))
            fail("缺少美元汇率时应拒绝更换本位币")
        } catch (error: LedgerException.ExchangeRateUnavailable) {
            assertNotNull(error)
        }
        assertEquals("CNY", database.ledgerDao().findById(ledgerId)!!.currencyCode)
        assertEquals(714L, database.transactionDao().findById(usdExpense)!!.baseAmountMinor)
        assertEquals(100L, database.transactionDao().findById(usdExpense)!!.amountMinor)

        rates.save(ExchangeRateSnapshot("USD", mapOf("CNY" to BigDecimal("7")), 1_000L, "early"))
        rates.save(ExchangeRateSnapshot("USD", mapOf("CNY" to BigDecimal("5")), 9_000L, "late"))
        assertEquals(2, database.exchangeRateDao().history("USD").size)

        ledgers.updateLedger(ledger.copy(currencyCode = "USD"))
        assertEquals("USD", database.ledgerDao().findById(ledgerId)!!.currencyCode)

        val usdRow = database.transactionDao().findById(usdExpense)!!
        assertEquals(100L, usdRow.amountMinor)
        assertEquals(100L, usdRow.baseAmountMinor)
        assertEquals("1", usdRow.fxRate)

        val datedRow = database.transactionDao().findById(dated)!!
        assertEquals(700L, datedRow.amountMinor)
        assertEquals(100L, datedRow.baseAmountMinor)

        val earlierRow = database.transactionDao().findById(earlier)!!
        assertEquals(140L, earlierRow.baseAmountMinor)

        val transferRow = database.transactionDao().findById(transfer)!!
        assertEquals(100L, transferRow.amountMinor)
        assertEquals(700L, transferRow.toAmountMinor)
        assertEquals(100L, transferRow.baseAmountMinor)

        val changed = ledgers.revalueWithCurrentRates(ledgerId)
        assertEquals(1, changed)
        assertEquals(140L, database.transactionDao().findById(dated)!!.baseAmountMinor)
        assertEquals(700L, database.transactionDao().findById(transfer)!!.toAmountMinor)
        assertEquals(100L, database.transactionDao().findById(usdExpense)!!.baseAmountMinor)
    }

    @Test
    fun savingARateAppendsHistoryWithoutReplacingOlderPublications() = runTest {
        rates.save(ExchangeRateSnapshot("USD", mapOf("CNY" to BigDecimal("7")), 1_000L, "early"))
        rates.save(ExchangeRateSnapshot("USD", mapOf("CNY" to BigDecimal("7")), 1_000L, "duplicate"))
        rates.save(ExchangeRateSnapshot("USD", mapOf("CNY" to BigDecimal("5")), 9_000L, "late"))

        val history = database.exchangeRateDao().history("USD")
        assertEquals(listOf(1_000L, 9_000L), history.map { it.fetchedAt })
        assertEquals("early", history.first().source)
        assertEquals("late", database.exchangeRateDao().find("USD")!!.source)
    }

    private fun expense(amountMinor: Long, accountId: String, at: Long) = SaveTransactionCommand(
        ledgerId = ledgerId,
        type = TransactionType.EXPENSE,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = foodId,
        transactionAt = at,
    )
}
