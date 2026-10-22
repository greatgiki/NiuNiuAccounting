package com.example.localledger.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.ResumeChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.time.ZoneIdProvider
import com.example.localledger.domain.usecase.GenerateRecurringTransactionsUseCase
import com.example.localledger.domain.usecase.ResumeRecurringRuleUseCase
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import com.example.localledger.domain.usecase.SaveTransferUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecurringResumeDeleteTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-03-15T04:00:00Z"), zone)
    private lateinit var database: WalletDatabase
    private lateinit var rules: RecurringRuleRepositoryImpl
    private lateinit var resume: ResumeRecurringRuleUseCase
    private lateinit var ledgerId: String
    private lateinit var cashId: String
    private lateinit var foodId: String

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
            .build()
        val source = WalletDatabaseSource { database }
        val ledgers = LedgerRepositoryImpl(source, clock)
        val accounts = AccountRepositoryImpl(source, clock)
        val categories = CategoryRepositoryImpl(source, clock)
        val transactions = TransactionRepositoryImpl(source, ExchangeRateRepositoryImpl(source), clock, com.example.localledger.data.repository.MerchantCategoryBook(source, clock))
        rules = RecurringRuleRepositoryImpl(source, clock)
        val generate = GenerateRecurringTransactionsUseCase(
            rules = rules,
            transactions = transactions,
            saveIncomeExpense = SaveIncomeExpenseUseCase(transactions),
            saveTransfer = SaveTransferUseCase(transactions),
            zoneIdProvider = ZoneIdProvider { zone },
            clock = clock,
        )
        resume = ResumeRecurringRuleUseCase(rules, generate, ZoneIdProvider { zone }, clock)
        ledgerId = ledgers.createLedger("日常")
        cashId = accounts.observeAccounts(ledgerId).first().first { it.name == "现金" }.id
        foodId = categories.observeCategories(ledgerId, CategoryType.EXPENSE).first().first { it.name == "餐饮" }.id
    }

    @After
    fun close() = database.close()

    @Test
    fun backfillResumeGeneratesMissedDates() = runTest {
        rules.save(pausedMonthly("backfill"))
        assertEquals(3, resume(ledgerId, "backfill", ResumeChoice.BACKFILL))
        assertEquals(3, database.transactionDao().listByLedger(ledgerId).size)
        val rule = database.recurringRuleDao().findById("backfill")!!
        assertEquals(false, rule.paused)
        assertEquals("2026-04-01", rule.nextRunDate)
    }

    @Test
    fun skipResumeMovesNextRunPastTodayWithoutWritingTransactions() = runTest {
        rules.save(pausedMonthly("skip"))
        assertEquals(0, resume(ledgerId, "skip", ResumeChoice.SKIP_MISSED))
        assertEquals(0, database.transactionDao().listByLedger(ledgerId).size)
        val rule = database.recurringRuleDao().findById("skip")!!
        assertEquals(false, rule.paused)
        assertEquals("2026-04-01", rule.nextRunDate)
    }

    @Test
    fun deleteCanKeepOrRemoveGeneratedTransactions() = runTest {
        rules.save(pausedMonthly("keep").copy(paused = false, nextRunDate = LocalDate.of(2026, 3, 1)))
        rules.save(pausedMonthly("drop").copy(paused = false, nextRunDate = LocalDate.of(2026, 3, 1)))
        assertEquals(1, resume(ledgerId, "keep", ResumeChoice.BACKFILL))
        assertEquals(1, resume(ledgerId, "drop", ResumeChoice.BACKFILL))
        val keptId = database.recurringRuleDao().occurrenceTransactionIds("keep").single()
        val droppedId = database.recurringRuleDao().occurrenceTransactionIds("drop").single()

        rules.delete(ledgerId, "keep", deleteGenerated = false)
        assertNull(database.recurringRuleDao().findById("keep"))
        assertNotNull(database.transactionDao().findById(keptId))

        rules.delete(ledgerId, "drop", deleteGenerated = true)
        assertNull(database.recurringRuleDao().findById("drop"))
        assertNull(database.transactionDao().findById(droppedId))
        assertNotNull(database.transactionDao().findById(keptId))
    }

    private fun pausedMonthly(id: String) = RecurringRule(
        id = id,
        ledgerId = ledgerId,
        type = TransactionType.EXPENSE,
        amountMinor = 1_000,
        toAmountMinor = null,
        accountId = cashId,
        toAccountId = null,
        categoryId = foodId,
        note = "房租",
        period = RecurrencePeriod.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        nextRunDate = LocalDate.of(2026, 1, 1),
        paused = true,
        createdAt = 0,
        updatedAt = 0,
    )
}
