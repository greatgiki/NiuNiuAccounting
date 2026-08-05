package com.example.localledger.data.local

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
import com.example.localledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalletDatabaseTest {
    private lateinit var database: WalletDatabase

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
    fun incomeExpenseAndTransferProduceCorrectBalancesAndSummary() = runTest {
        seedLedger("ledger-a")
        val now = 1_700_000_000_000L
        insertTransaction("income", TransactionType.INCOME, 10_000, "account-a", null, "income-a", now)
        insertTransaction("expense", TransactionType.EXPENSE, 2_500, "account-a", null, "expense-a", now + 1)
        insertTransaction("transfer", TransactionType.TRANSFER, 3_000, "account-a", "account-b", null, now + 2)

        val balances = database.accountDao().observeBalances("ledger-a").first()
        assertEquals(4_500L, balances.single { it.account.id == "account-a" }.balanceMinor)
        assertEquals(3_000L, balances.single { it.account.id == "account-b" }.balanceMinor)

        val summary = database.transactionDao().observeSummary("ledger-a", now, now + 10).first()
        assertEquals(10_000L, summary.incomeMinor)
        assertEquals(2_500L, summary.expenseMinor)
    }

    @Test
    fun deletingTransactionRefreshesDerivedQueries() = runTest {
        seedLedger("ledger-a")
        val now = 1_700_000_000_000L
        insertTransaction("expense", TransactionType.EXPENSE, 2_500, "account-a", null, "expense-a", now)
        assertEquals(1, database.transactionDao().deleteById("expense"))

        val balance = database.accountDao().observeBalances("ledger-a").first()
            .single { it.account.id == "account-a" }
        val summary = database.transactionDao().observeSummary("ledger-a", now, now + 1).first()
        assertEquals(0L, balance.balanceMinor)
        assertEquals(0L, summary.expenseMinor)
    }

    @Test
    fun filterMatchesTransferDestinationAndLiteralPercentKeyword() = runTest {
        seedLedger("ledger-a")
        val now = 1_700_000_000_000L
        insertTransaction(
            "transfer", TransactionType.TRANSFER, 3_000,
            "account-a", "account-b", null, now, "50% 储蓄",
        )

        val result = database.transactionDao().observeDetails(
            "ledger-a", TransactionType.TRANSFER, null, "account-b",
            now, now + 1, "50%", 20,
        ).first()

        assertEquals(listOf("transfer"), result.map { it.transaction.id })
    }

    @Test
    fun ledgersRemainIsolated() = runTest {
        seedLedger("ledger-a")
        seedLedger("ledger-b", "-b")
        val now = 1_700_000_000_000L
        insertTransaction("a", TransactionType.INCOME, 100, "account-a", null, "income-a", now)
        insertTransaction(
            "b", TransactionType.INCOME, 900, "account-a-b", null, "income-a-b", now,
            ledgerId = "ledger-b",
        )

        assertEquals(
            100L,
            database.transactionDao().observeSummary("ledger-a", now, now + 1).first().incomeMinor,
        )
        assertEquals(
            900L,
            database.transactionDao().observeSummary("ledger-b", now, now + 1).first().incomeMinor,
        )
    }

    @Test
    fun databaseTriggerRejectsCrossLedgerReference() = runTest {
        seedLedger("ledger-a")
        seedLedger("ledger-b", "-b")

        val failure = runCatching {
            insertTransaction(
                "invalid", TransactionType.INCOME, 100,
                "account-a-b", null, "income-a", 1_700_000_000_000L,
            )
        }.exceptionOrNull()

        assertNotNull(failure)
    }

    private suspend fun seedLedger(ledgerId: String, suffix: String = "") {
        database.ledgerDao().insert(LedgerEntity(ledgerId, "Ledger$suffix", createdAt = 1L))
        database.accountDao().upsert(account("account-a$suffix", ledgerId, 0))
        database.accountDao().upsert(account("account-b$suffix", ledgerId, 1))
        database.categoryDao().upsert(category("expense-a$suffix", ledgerId, CategoryType.EXPENSE))
        database.categoryDao().upsert(category("income-a$suffix", ledgerId, CategoryType.INCOME))
    }

    private fun account(id: String, ledgerId: String, sortOrder: Int) = AccountEntity(
        id = id,
        ledgerId = ledgerId,
        name = id,
        type = AccountType.CASH,
        sortOrder = sortOrder,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun category(id: String, ledgerId: String, type: CategoryType) = CategoryEntity(
        id = id,
        ledgerId = ledgerId,
        name = id,
        type = type,
        iconKey = "other",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private suspend fun insertTransaction(
        id: String,
        type: TransactionType,
        amount: Long,
        accountId: String,
        toAccountId: String?,
        categoryId: String?,
        at: Long,
        note: String? = null,
        ledgerId: String = "ledger-a",
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
            ),
        )
    }
}
