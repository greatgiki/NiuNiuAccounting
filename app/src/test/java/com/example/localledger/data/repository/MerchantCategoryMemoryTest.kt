package com.example.localledger.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.usecase.SaveIncomeExpenseUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MerchantCategoryMemoryTest {
    private lateinit var database: WalletDatabase
    private val clock: Clock = Clock.systemUTC()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun changingATransactionCategoryRemembersTheMerchantAndGuessingUsesItFirst() = runTest {
        val source = WalletDatabaseSource { database }
        val book = MerchantCategoryBook(source, clock)
        val ledgers = LedgerRepositoryImpl(source, clock)
        val accounts = AccountRepositoryImpl(source, clock)
        val categories = CategoryRepositoryImpl(source, clock)
        val transactions = TransactionRepositoryImpl(source, EmptyRates, clock, book)
        val save = SaveIncomeExpenseUseCase(transactions)
        val ledgerId = ledgers.createLedger("日常")
        val cash = accounts.observeAccounts(ledgerId).first().first { it.name == "现金" }.id
        val expense = categories.observeCategories(ledgerId, CategoryType.EXPENSE).first()
        val food = expense.first { it.name == "餐饮" }.id
        val shop = expense.first { it.name == "购物" }.id

        val id = save(
            SaveTransactionCommand(
                ledgerId = ledgerId,
                type = TransactionType.EXPENSE,
                amountMinor = 1_250,
                accountId = cash,
                categoryId = food,
                transactionAt = 20L,
                counterparty = " 美团 ",
                rememberMerchantCategory = true,
            ),
        )
        assertEquals(food, book.findCategoryId(ledgerId, TransactionType.EXPENSE, "美团"))

        save(
            SaveTransactionCommand(
                transactionId = id,
                ledgerId = ledgerId,
                type = TransactionType.EXPENSE,
                amountMinor = 1_250,
                accountId = cash,
                categoryId = food,
                transactionAt = 20L,
                counterparty = "美团",
            ),
        )
        assertEquals(food, book.findCategoryId(ledgerId, TransactionType.EXPENSE, "美团"))

        save(
            SaveTransactionCommand(
                transactionId = id,
                ledgerId = ledgerId,
                type = TransactionType.EXPENSE,
                amountMinor = 1_800,
                accountId = cash,
                categoryId = shop,
                transactionAt = 20L,
            ),
        )
        assertEquals(shop, book.findCategoryId(ledgerId, TransactionType.EXPENSE, "美团"))
        assertEquals(1, book.observe(ledgerId).first().size)
        assertEquals("美团", database.transactionDao().findById(id)?.counterparty)

        val drafts = DraftRepository(source, transactions, clock, book)
        val draftId = drafts.insert(
            NewDraft(
                ledgerId = ledgerId,
                source = BillSource.WECHAT,
                type = TransactionType.EXPENSE,
                amountMinor = 900,
                counterparty = "瑞幸",
                rawText = "瑞幸",
                categoryId = food,
                accountId = cash,
                transactionAt = 30L,
                notificationKey = "notify-luckin",
                likelyDuplicate = false,
            ),
        )
        drafts.updateAssignment(draftId, food, cash)
        assertNull(book.findCategoryId(ledgerId, TransactionType.EXPENSE, "瑞幸"))
        drafts.updateAssignment(draftId, shop, cash)
        assertEquals(shop, book.findCategoryId(ledgerId, TransactionType.EXPENSE, "瑞幸"))

        book.clearAll(ledgerId)
        assertNull(book.findCategoryId(ledgerId, TransactionType.EXPENSE, "美团"))
    }
}

private object EmptyRates : ExchangeRateRepository {
    override fun observe(baseCode: String) = flowOf<ExchangeRateSnapshot?>(null)
    override suspend fun get(baseCode: String): ExchangeRateSnapshot? = null
    override suspend fun save(snapshot: ExchangeRateSnapshot) = Unit
    override suspend fun baseCodesInUse(): List<String> = emptyList()
}
