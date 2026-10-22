package com.example.localledger.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.localledger.data.local.converter.RoomConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localledger.data.local.dao.AccountDao
import com.example.localledger.data.local.dao.BackupDao
import com.example.localledger.data.local.dao.BudgetDao
import com.example.localledger.data.local.dao.CategoryDao
import com.example.localledger.data.local.dao.ExchangeRateDao
import com.example.localledger.data.local.dao.LedgerDao
import com.example.localledger.data.local.dao.MerchantCategoryDao
import com.example.localledger.data.local.dao.PendingDraftDao
import com.example.localledger.data.local.dao.RecurringRuleDao
import com.example.localledger.data.local.dao.TransactionDao
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.ExchangeRateHistoryEntity
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.MerchantCategoryRuleEntity
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.entity.TransactionEntity

internal const val WALLET_SCHEMA_VERSION = 6

@Database(
    entities = [
        LedgerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        RecurringOccurrenceEntity::class,
        ExchangeRateSnapshotEntity::class,
        ExchangeRateHistoryEntity::class,
        PendingDraftEntity::class,
        MerchantCategoryRuleEntity::class,
    ],
    version = WALLET_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun pendingDraftDao(): PendingDraftDao
    abstract fun backupDao(): BackupDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao

    companion object {
        const val FILE_NAME = "ledger.db"
        const val SCHEMA_VERSION = WALLET_SCHEMA_VERSION
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        val INTEGRITY_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installIntegrityTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
                installIntegrityTriggers(db)
            }
        }

        private fun installIntegrityTriggers(db: SupportSQLiteDatabase) {
            listOf("INSERT" to "insert", "UPDATE" to "update").forEach { (operation, suffix) ->
                db.execSQL("DROP TRIGGER IF EXISTS validate_transaction_$suffix")
                db.execSQL(
                    """
                    CREATE TRIGGER validate_transaction_$suffix
                    BEFORE $operation ON transactions
                    FOR EACH ROW
                    WHEN
                        NEW.amount_minor <= 0
                        OR NEW.base_amount_minor <= 0
                        OR NEW.type NOT IN ('EXPENSE', 'INCOME', 'TRANSFER')
                        OR (
                            NEW.type = 'TRANSFER'
                            AND (NEW.to_amount_minor IS NULL OR NEW.to_amount_minor <= 0)
                        )
                        OR (
                            NEW.type != 'TRANSFER'
                            AND NEW.to_amount_minor IS NOT NULL
                        )
                        OR (
                            NEW.type IN ('EXPENSE', 'INCOME')
                            AND (NEW.category_id IS NULL OR NEW.to_account_id IS NOT NULL)
                        )
                        OR (
                            NEW.type = 'TRANSFER'
                            AND (
                                NEW.to_account_id IS NULL
                                OR NEW.category_id IS NOT NULL
                                OR NEW.account_id = NEW.to_account_id
                            )
                        )
                        OR NOT EXISTS (
                            SELECT 1 FROM accounts
                            WHERE id = NEW.account_id AND ledger_id = NEW.ledger_id
                        )
                        OR (
                            NEW.to_account_id IS NOT NULL
                            AND NOT EXISTS (
                                SELECT 1 FROM accounts
                                WHERE id = NEW.to_account_id AND ledger_id = NEW.ledger_id
                            )
                        )
                        OR (
                            NEW.category_id IS NOT NULL
                            AND NOT EXISTS (
                                SELECT 1 FROM categories
                                WHERE id = NEW.category_id AND ledger_id = NEW.ledger_id
                            )
                        )
                        OR (
                            NEW.category_id IS NOT NULL
                            AND NOT EXISTS (
                                SELECT 1 FROM categories
                                WHERE id = NEW.category_id AND type = NEW.type
                            )
                        )
                    BEGIN
                        SELECT RAISE(ABORT, 'invalid transaction');
                    END
                    """.trimIndent(),
                )
            }
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS prevent_retyping_used_category
                BEFORE UPDATE OF type ON categories
                FOR EACH ROW
                WHEN NEW.type != OLD.type
                    AND EXISTS (
                        SELECT 1 FROM transactions WHERE category_id = OLD.id
                    )
                BEGIN
                    SELECT RAISE(ABORT, 'cannot retype used category');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS prevent_account_ledger_change
                BEFORE UPDATE OF ledger_id ON accounts
                FOR EACH ROW
                WHEN NEW.ledger_id != OLD.ledger_id
                BEGIN
                    SELECT RAISE(ABORT, 'cannot change account ledger');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS prevent_category_ledger_change
                BEFORE UPDATE OF ledger_id ON categories
                FOR EACH ROW
                WHEN NEW.ledger_id != OLD.ledger_id
                BEGIN
                    SELECT RAISE(ABORT, 'cannot change category ledger');
                END
                """.trimIndent(),
            )
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN to_amount_minor INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN base_amount_minor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN fx_rate TEXT NOT NULL DEFAULT '1'")
        db.execSQL("UPDATE transactions SET base_amount_minor = amount_minor")
        db.execSQL("UPDATE transactions SET to_amount_minor = amount_minor WHERE type = 'TRANSFER'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
              `id` TEXT NOT NULL,
              `ledger_id` TEXT NOT NULL,
              `year_month` TEXT NOT NULL,
              `category_id` TEXT,
              `amount_minor` INTEGER NOT NULL,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`ledger_id`) REFERENCES `ledgers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_ledger_id_year_month` ON `budgets` (`ledger_id`, `year_month`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_rules` (
              `id` TEXT NOT NULL,
              `ledger_id` TEXT NOT NULL,
              `type` TEXT NOT NULL,
              `amount_minor` INTEGER NOT NULL,
              `to_amount_minor` INTEGER,
              `account_id` TEXT NOT NULL,
              `to_account_id` TEXT,
              `category_id` TEXT,
              `note` TEXT,
              `period` TEXT NOT NULL,
              `start_date` TEXT NOT NULL,
              `end_date` TEXT,
              `next_run_date` TEXT NOT NULL,
              `paused` INTEGER NOT NULL DEFAULT 0,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`ledger_id`) REFERENCES `ledgers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`to_account_id`) REFERENCES `accounts`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_ledger_id_paused` ON `recurring_rules` (`ledger_id`, `paused`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_account_id` ON `recurring_rules` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_to_account_id` ON `recurring_rules` (`to_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_category_id` ON `recurring_rules` (`category_id`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_occurrences` (
              `rule_id` TEXT NOT NULL,
              `occurrence_date` TEXT NOT NULL,
              `transaction_id` TEXT NOT NULL,
              PRIMARY KEY(`rule_id`, `occurrence_date`),
              FOREIGN KEY(`rule_id`) REFERENCES `recurring_rules`(`id`) ON UPDATE RESTRICT ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exchange_rate_snapshots` (
              `base_code` TEXT NOT NULL,
              `rates_json` TEXT NOT NULL,
              `fetched_at` INTEGER NOT NULL,
              `source` TEXT NOT NULL,
              PRIMARY KEY(`base_code`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exchange_rate_history` (
              `base_code` TEXT NOT NULL,
              `fetched_at` INTEGER NOT NULL,
              `rates_json` TEXT NOT NULL,
              `source` TEXT NOT NULL,
              PRIMARY KEY(`base_code`, `fetched_at`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `exchange_rate_history` (`base_code`, `fetched_at`, `rates_json`, `source`)
            SELECT `base_code`, `fetched_at`, `rates_json`, `source` FROM `exchange_rate_snapshots`
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `external_source` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `external_id` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_ledger_id_external_source_external_id`
            ON `transactions` (`ledger_id`, `external_source`, `external_id`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_drafts` (
              `id` TEXT NOT NULL,
              `ledger_id` TEXT NOT NULL,
              `source` TEXT NOT NULL,
              `type` TEXT NOT NULL,
              `amount_minor` INTEGER NOT NULL,
              `counterparty` TEXT,
              `note` TEXT,
              `raw_text` TEXT NOT NULL,
              `category_id` TEXT,
              `account_id` TEXT,
              `transaction_at` INTEGER NOT NULL,
              `status` TEXT NOT NULL,
              `transaction_id` TEXT,
              `notification_key` TEXT,
              `likely_duplicate` INTEGER NOT NULL DEFAULT 0,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`ledger_id`) REFERENCES `ledgers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_drafts_ledger_id_status` ON `pending_drafts` (`ledger_id`, `status`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_drafts_account_id` ON `pending_drafts` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_drafts_category_id` ON `pending_drafts` (`category_id`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_drafts_notification_key` ON `pending_drafts` (`notification_key`)",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pending_drafts` ADD COLUMN `needs_amount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `pending_drafts` ADD COLUMN `sbn_key` TEXT")
        db.execSQL("ALTER TABLE `pending_drafts` ADD COLUMN `group_key` TEXT")
        db.execSQL(
            """
            UPDATE `pending_drafts`
            SET `sbn_key` = `notification_key`
            WHERE `sbn_key` IS NULL AND `notification_key` IS NOT NULL
            """.trimIndent(),
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `counterparty` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `merchant_category_rules` (
              `id` TEXT NOT NULL,
              `ledger_id` TEXT NOT NULL,
              `merchant_key` TEXT NOT NULL,
              `merchant_label` TEXT NOT NULL,
              `type` TEXT NOT NULL,
              `category_id` TEXT NOT NULL,
              `updated_at` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`ledger_id`) REFERENCES `ledgers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_category_rules_ledger_id_merchant_key_type`
            ON `merchant_category_rules` (`ledger_id`, `merchant_key`, `type`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_merchant_category_rules_category_id`
            ON `merchant_category_rules` (`category_id`)
            """.trimIndent(),
        )
    }
}
