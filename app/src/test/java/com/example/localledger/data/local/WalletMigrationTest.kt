package com.example.localledger.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localledger.data.local.database.WalletDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WalletDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2CopiesMoneyAndAddsNewTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO ledgers (id, name, currency_code, created_at, is_archived)
                VALUES ('ledger', '日常', 'CNY', 1, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO accounts (id, ledger_id, name, type, initial_balance_minor, sort_order, is_archived, created_at, updated_at)
                VALUES ('cash', 'ledger', '现金', 'CASH', 0, 0, 0, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO accounts (id, ledger_id, name, type, initial_balance_minor, sort_order, is_archived, created_at, updated_at)
                VALUES ('bank', 'ledger', '银行卡', 'BANK_CARD', 0, 1, 0, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO categories (id, ledger_id, name, type, icon_key, sort_order, is_archived, created_at, updated_at)
                VALUES ('food', 'ledger', '餐饮', 'EXPENSE', 'restaurant', 0, 0, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions (
                  id, ledger_id, type, amount_minor, account_id, to_account_id, category_id,
                  transaction_at, note, created_at, updated_at
                ) VALUES ('expense', 'ledger', 'EXPENSE', 2500, 'cash', NULL, 'food', 10, NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions (
                  id, ledger_id, type, amount_minor, account_id, to_account_id, category_id,
                  transaction_at, note, created_at, updated_at
                ) VALUES ('transfer', 'ledger', 'TRANSFER', 3000, 'cash', 'bank', NULL, 11, NULL, 11, 11)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            *WalletDatabase.MIGRATIONS,
        )
        migrated.query("SELECT base_amount_minor, to_amount_minor, fx_rate FROM transactions WHERE id = 'expense'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2500L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            assertEquals("1", cursor.getString(2))
        }
        migrated.query("SELECT to_amount_minor FROM transactions WHERE id = 'transfer'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3000L, cursor.getLong(0))
        }
        migrated.query("SELECT currency_code FROM accounts WHERE id = 'cash'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CNY", cursor.getString(0))
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('budgets', 'recurring_rules', 'recurring_occurrences', 'exchange_rate_snapshots')",
        ).use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(0)
            assertEquals(
                setOf("budgets", "recurring_rules", "recurring_occurrences", "exchange_rate_snapshots"),
                names.toSet(),
            )
        }
        migrated.close()
    }

    @Test
    fun migrate2To3CopiesTheCurrentSnapshotIntoHistory() {
        helper.createDatabase(HISTORY_DB, 2).apply {
            execSQL(
                """
                INSERT INTO exchange_rate_snapshots (base_code, rates_json, fetched_at, source)
                VALUES ('CNY', '{"USD":"0.14"}', 42, 'cache')
                """.trimIndent(),
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(HISTORY_DB, 3, true, *WalletDatabase.MIGRATIONS)
        migrated.query("SELECT rates_json, fetched_at, source FROM exchange_rate_history WHERE base_code = 'CNY'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"USD\":\"0.14\"}", cursor.getString(0))
            assertEquals(42L, cursor.getLong(1))
            assertEquals("cache", cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4AddsExternalIdentityAndDrafts() {
        helper.createDatabase(IMPORT_DB, 3).apply {
            execSQL(
                """
                INSERT INTO ledgers (id, name, currency_code, created_at, is_archived)
                VALUES ('ledger', '日常', 'CNY', 1, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO accounts (id, ledger_id, name, type, initial_balance_minor, sort_order, is_archived, created_at, updated_at, currency_code)
                VALUES ('cash', 'ledger', '现金', 'CASH', 0, 0, 0, 1, 1, 'CNY')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO categories (id, ledger_id, name, type, icon_key, sort_order, is_archived, created_at, updated_at)
                VALUES ('food', 'ledger', '餐饮', 'EXPENSE', 'restaurant', 0, 0, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions (
                  id, ledger_id, type, amount_minor, account_id, to_account_id, category_id,
                  transaction_at, note, created_at, updated_at, to_amount_minor, base_amount_minor, fx_rate
                ) VALUES (
                  'expense', 'ledger', 'EXPENSE', 2500, 'cash', NULL, 'food',
                  10, NULL, 10, 10, NULL, 2500, '1'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(IMPORT_DB, 4, true, *WalletDatabase.MIGRATIONS)
        migrated.query("SELECT external_source, external_id, origin FROM transactions WHERE id = 'expense'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertEquals("MANUAL", cursor.getString(2))
        }
        migrated.execSQL(
            """
            UPDATE transactions
            SET external_source = 'WECHAT', external_id = '42001'
            WHERE id = 'expense'
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO pending_drafts (
              id, ledger_id, source, type, amount_minor, counterparty, note, raw_text,
              category_id, account_id, transaction_at, status, transaction_id, notification_key,
              likely_duplicate, created_at, updated_at
            ) VALUES (
              'draft', 'ledger', 'WECHAT', 'EXPENSE', 1250, '美团', NULL, '微信支付 已支付¥12.50',
              'food', 'cash', 20, 'PENDING', NULL, 'notify-1',
              0, 20, 20
            )
            """.trimIndent(),
        )
        migrated.query("SELECT status, source FROM pending_drafts WHERE id = 'draft'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("PENDING", cursor.getString(0))
            assertEquals("WECHAT", cursor.getString(1))
        }
        var rejected = false
        try {
            migrated.execSQL(
                """
                INSERT INTO transactions (
                  id, ledger_id, type, amount_minor, account_id, to_account_id, category_id,
                  transaction_at, note, created_at, updated_at, to_amount_minor, base_amount_minor, fx_rate,
                  external_source, external_id, origin
                ) VALUES (
                  'dup', 'ledger', 'EXPENSE', 100, 'cash', NULL, 'food',
                  11, NULL, 11, 11, NULL, 100, '1',
                  'WECHAT', '42001', 'IMPORT'
                )
                """.trimIndent(),
            )
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue(rejected)
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val HISTORY_DB = "migration-history"
        const val IMPORT_DB = "migration-import"
    }
}
