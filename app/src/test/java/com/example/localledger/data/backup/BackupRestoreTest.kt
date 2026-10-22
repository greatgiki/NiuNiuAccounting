package com.example.localledger.data.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.local.database.WALLET_SCHEMA_VERSION
import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.ExchangeRateHistoryEntity
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRestoreTest {
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
    fun encryptedBackupRestoresEveryTableAndReplacesCurrentData() = runTest {
        seedOriginal()
        val original = BackupStore.read(database, 50L)
        val bytes = BackupCodec.encode(original, "ledger-password".toCharArray(), iterations = 1_000)
        database.ledgerDao().insert(LedgerEntity("other", "临时", createdAt = 9L))
        database.accountDao().upsert(AccountEntity("other-cash", "other", "临时现金", AccountType.CASH, createdAt = 9L, updatedAt = 9L))

        var safety: BackupDocument? = null
        val decoded = BackupCodec.decode(bytes, "ledger-password".toCharArray())
        BackupStore.restore(database, decoded, safetyCreatedAtEpochMillis = 60L) { snapshot ->
            safety = snapshot
        }

        assertEquals("临时", safety?.tables?.ledgers?.single { it.id == "other" }?.name)
        assertEquals(listOf("ledger"), database.backupDao().ledgers().map { it.id })
        assertEquals("午饭", database.transactionDao().findById("tx")?.note)
        assertEquals(1, database.backupDao().budgets().size)
        assertEquals(1, database.backupDao().recurringRules().size)
        assertEquals(1, database.backupDao().recurringOccurrences().size)
        assertEquals("cfets", database.exchangeRateDao().find("CNY")?.source)
        assertEquals(1, database.exchangeRateDao().history("CNY").size)
        assertEquals("星巴克", database.pendingDraftDao().findById("draft")?.counterparty)
        assertNull(database.ledgerDao().findById("other"))
        assertEquals(WALLET_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun invalidBackupDoesNotWriteSafetyCopyOrChangeRows() = runTest {
        seedOriginal()
        val original = BackupStore.read(database, 50L)
        val broken = original.copy(
            tables = original.tables.copy(
                transactions = original.tables.transactions.map { it.copy(accountId = "missing") },
            ),
        )
        var safetyCalled = false
        val error = runCatching {
            BackupStore.restore(database, broken, 70L) { safetyCalled = true }
        }.exceptionOrNull()
        assertTrue(error is BackupFailure)
        assertTrue(error!!.message!!.contains("账户"))
        assertEquals(false, safetyCalled)
        assertEquals("午饭", database.transactionDao().findById("tx")?.note)
    }

    private suspend fun seedOriginal() {
        database.ledgerDao().insert(LedgerEntity("ledger", "日常", createdAt = 1L))
        database.accountDao().upsert(
            AccountEntity("cash", "ledger", "现金", AccountType.CASH, createdAt = 1L, updatedAt = 1L),
        )
        database.accountDao().upsert(
            AccountEntity("bank", "ledger", "银行卡", AccountType.BANK_CARD, createdAt = 1L, updatedAt = 1L),
        )
        database.categoryDao().upsert(
            CategoryEntity("food", "ledger", "餐饮", CategoryType.EXPENSE, "food", createdAt = 1L, updatedAt = 1L),
        )
        database.transactionDao().upsert(
            TransactionEntity(
                id = "tx",
                ledgerId = "ledger",
                type = TransactionType.EXPENSE,
                amountMinor = 1_250,
                accountId = "cash",
                toAccountId = null,
                categoryId = "food",
                transactionAt = 20L,
                note = "午饭",
                createdAt = 20L,
                updatedAt = 20L,
                baseAmountMinor = 1_250,
                fxRate = "1",
            ),
        )
        database.transactionDao().upsert(
            TransactionEntity(
                id = "move",
                ledgerId = "ledger",
                type = TransactionType.TRANSFER,
                amountMinor = 500,
                accountId = "cash",
                toAccountId = "bank",
                categoryId = null,
                transactionAt = 21L,
                note = null,
                createdAt = 21L,
                updatedAt = 21L,
                toAmountMinor = 500,
                baseAmountMinor = 500,
                fxRate = "1",
            ),
        )
        database.budgetDao().upsert(
            BudgetEntity("budget", "ledger", "2026-10", "food", 20_000, 1L, 1L),
        )
        database.recurringRuleDao().upsert(
            RecurringRuleEntity(
                id = "rule",
                ledgerId = "ledger",
                type = TransactionType.EXPENSE,
                amountMinor = 300,
                toAmountMinor = null,
                accountId = "cash",
                toAccountId = null,
                categoryId = "food",
                note = "地铁",
                period = RecurrencePeriod.MONTHLY,
                startDate = "2026-10-01",
                endDate = null,
                nextRunDate = "2026-11-01",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.recurringRuleDao().insertOccurrence(RecurringOccurrenceEntity("rule", "2026-10-01", "tx"))
        database.exchangeRateDao().upsert(ExchangeRateSnapshotEntity("CNY", """{"USD":0.14}""", 30L, "cfets"))
        database.exchangeRateDao().insertHistory(ExchangeRateHistoryEntity("CNY", 30L, """{"USD":0.14}""", "cfets"))
        database.pendingDraftDao().upsert(
            PendingDraftEntity(
                id = "draft",
                ledgerId = "ledger",
                source = "WECHAT",
                type = TransactionType.EXPENSE,
                amountMinor = 660,
                counterparty = "星巴克",
                note = null,
                rawText = "你已付款6.60元",
                categoryId = "food",
                accountId = "cash",
                transactionAt = 40L,
                status = "PENDING",
                transactionId = null,
                notificationKey = "notice-1",
                createdAt = 40L,
                updatedAt = 40L,
            ),
        )
    }
}
