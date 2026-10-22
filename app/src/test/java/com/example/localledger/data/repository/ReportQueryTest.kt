package com.example.localledger.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.time.DateRangeResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReportQueryTest {
    private lateinit var database: WalletDatabase
    private lateinit var repository: StatisticsRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
            .build()
        repository = StatisticsRepositoryImpl(
            databaseProvider = { database },
            dateRangeResolver = DateRangeResolver { ZoneId.of("Asia/Shanghai") },
        )
    }

    @After
    fun close() = database.close()

    @Test
    fun largestExpenseAndFrequencyStayInsideTheLedgerAndRange() = runTest {
        seed()
        val start = Instant.parse("2026-07-20T16:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-07-22T16:00:00Z").toEpochMilli()
        val morning = Instant.parse("2026-07-21T02:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-07-21T10:00:00Z").toEpochMilli()
        insert("food-1", TransactionType.EXPENSE, 1_000, "food", morning, "早餐")
        insert("food-2", TransactionType.EXPENSE, 1_000, "food", morning + 1, null)
        insert("food-3", TransactionType.EXPENSE, 1_000, "food", morning + 2, null)
        insert("shop-early", TransactionType.EXPENSE, 8_000, "shop", morning + 3, "早")
        insert("shop-late", TransactionType.EXPENSE, 8_000, "shop", evening, "晚饭")
        insert("shop-small", TransactionType.EXPENSE, 2_500, "shop", evening + 1, null)
        insert("income", TransactionType.INCOME, 50_000, "income", evening, null)
        insert(
            "transfer",
            TransactionType.TRANSFER,
            9_000,
            null,
            evening,
            null,
            toAccountId = "account-b",
        )
        insert("outside", TransactionType.EXPENSE, 999_999, "shop", end, "不应计入")
        insert(
            "other-ledger",
            TransactionType.EXPENSE,
            999_999,
            "shop-b",
            evening,
            null,
            ledgerId = "ledger-b",
            accountId = "account-b-ledger",
        )

        val range = DateTimeRange(start, end)
        val largest = repository.observeLargestExpense("ledger-a", range).first()
        assertEquals("购物", largest?.categoryName)
        assertEquals(8_000L, largest?.amountMinor)
        assertEquals("晚饭", largest?.note)
        assertEquals(LocalDate.of(2026, 7, 21), largest?.date)

        val counts = repository.observeExpenseFrequency("ledger-a", range).first()
        assertEquals(listOf("购物", "餐饮"), counts.map { it.categoryName })
        assertEquals(3, counts[0].count)
        assertEquals(18_500L, counts[0].amountMinor)
        assertEquals(3, counts[1].count)
        assertEquals(3_000L, counts[1].amountMinor)
        assertEquals("shop", counts[0].categoryId)
    }

    @Test
    fun blankNoteIsOmittedAndAnEmptyRangeHasNoLargestExpense() = runTest {
        seed()
        val at = Instant.parse("2026-07-21T02:00:00Z").toEpochMilli()
        insert("only", TransactionType.EXPENSE, 2_000, "food", at, "   ")
        val range = DateTimeRange(at, at + 1)
        val largest = repository.observeLargestExpense("ledger-a", range).first()
        assertEquals("餐饮", largest?.categoryName)
        assertNull(largest?.note)
        assertEquals(
            null,
            repository.observeLargestExpense("ledger-a", DateTimeRange(at + 10, at + 20)).first(),
        )
    }

    private suspend fun seed() {
        database.ledgerDao().insert(LedgerEntity("ledger-a", "日常", createdAt = 1L))
        database.ledgerDao().insert(LedgerEntity("ledger-b", "其他", createdAt = 1L))
        database.accountDao().upsert(account("account-a", "ledger-a"))
        database.accountDao().upsert(account("account-b", "ledger-a"))
        database.accountDao().upsert(account("account-b-ledger", "ledger-b"))
        database.categoryDao().upsert(category("food", "ledger-a", "餐饮", CategoryType.EXPENSE))
        database.categoryDao().upsert(category("shop", "ledger-a", "购物", CategoryType.EXPENSE))
        database.categoryDao().upsert(category("income", "ledger-a", "工资", CategoryType.INCOME))
        database.categoryDao().upsert(category("shop-b", "ledger-b", "购物", CategoryType.EXPENSE))
    }

    private fun account(id: String, ledgerId: String) = AccountEntity(
        id = id,
        ledgerId = ledgerId,
        name = id,
        type = AccountType.CASH,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun category(id: String, ledgerId: String, name: String, type: CategoryType) = CategoryEntity(
        id = id,
        ledgerId = ledgerId,
        name = name,
        type = type,
        iconKey = "other",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private suspend fun insert(
        id: String,
        type: TransactionType,
        amount: Long,
        categoryId: String?,
        at: Long,
        note: String?,
        ledgerId: String = "ledger-a",
        accountId: String = "account-a",
        toAccountId: String? = null,
    ) {
        database.transactionDao().upsert(
            TransactionEntity(
                id = id,
                ledgerId = ledgerId,
                type = type,
                amountMinor = amount,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                transactionAt = at,
                note = note,
                createdAt = at,
                updatedAt = at,
                toAmountMinor = if (type == TransactionType.TRANSFER) amount else null,
                baseAmountMinor = amount,
                fxRate = "1",
            ),
        )
    }
}
