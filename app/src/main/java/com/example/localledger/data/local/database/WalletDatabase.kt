package com.example.localledger.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localledger.data.local.converter.RoomConverters
import com.example.localledger.data.local.dao.AccountDao
import com.example.localledger.data.local.dao.CategoryDao
import com.example.localledger.data.local.dao.LedgerDao
import com.example.localledger.data.local.dao.TransactionDao
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.TransactionEntity

@Database(
    entities = [
        LedgerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val FILE_NAME = "ledger.db"
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

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
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS validate_transaction_$suffix
                    BEFORE $operation ON transactions
                    FOR EACH ROW
                    WHEN
                        NEW.amount_minor <= 0
                        OR NEW.type NOT IN ('EXPENSE', 'INCOME', 'TRANSFER')
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
