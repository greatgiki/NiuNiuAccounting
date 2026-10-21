package com.example.localledger.data.backup

import androidx.room.withTransaction
import com.example.localledger.data.local.database.WalletDatabase

internal object BackupStore {
    suspend fun read(database: WalletDatabase, createdAtEpochMillis: Long): BackupDocument {
        return database.withTransaction {
            val dao = database.backupDao()
            BackupDocument(
                schemaVersion = WalletDatabase.SCHEMA_VERSION,
                createdAtEpochMillis = createdAtEpochMillis,
                tables = BackupTables(
                    ledgers = dao.ledgers().map { it.toRow() },
                    accounts = dao.accounts().map { it.toRow() },
                    categories = dao.categories().map { it.toRow() },
                    transactions = dao.transactions().map { it.toRow() },
                    budgets = dao.budgets().map { it.toRow() },
                    recurringRules = dao.recurringRules().map { it.toRow() },
                    recurringOccurrences = dao.recurringOccurrences().map { it.toRow() },
                    exchangeRateSnapshots = dao.rateSnapshots().map { it.toRow() },
                    exchangeRateHistory = dao.rateHistory().map { it.toRow() },
                    pendingDrafts = dao.pendingDrafts().map { it.toRow() },
                ),
            )
        }
    }

    suspend fun restore(
        database: WalletDatabase,
        document: BackupDocument,
        safetyCreatedAtEpochMillis: Long,
        writeSafety: suspend (BackupDocument) -> Unit,
    ) {
        BackupValidator.validate(document)
        val safety = read(database, safetyCreatedAtEpochMillis)
        writeSafety(safety)
        database.withTransaction {
            val dao = database.backupDao()
            dao.deleteOccurrences()
            dao.deleteDrafts()
            dao.deleteRules()
            dao.deleteBudgets()
            dao.deleteTransactions()
            dao.deleteCategories()
            dao.deleteAccounts()
            dao.deleteRateHistory()
            dao.deleteRateSnapshots()
            dao.deleteLedgers()
            val tables = document.tables
            if (tables.ledgers.isNotEmpty()) dao.insertLedgers(tables.ledgers.map { it.toEntity() })
            if (tables.accounts.isNotEmpty()) dao.insertAccounts(tables.accounts.map { it.toEntity() })
            if (tables.categories.isNotEmpty()) dao.insertCategories(tables.categories.map { it.toEntity() })
            if (tables.transactions.isNotEmpty()) dao.insertTransactions(tables.transactions.map { it.toEntity() })
            if (tables.budgets.isNotEmpty()) dao.insertBudgets(tables.budgets.map { it.toEntity() })
            if (tables.recurringRules.isNotEmpty()) dao.insertRules(tables.recurringRules.map { it.toEntity() })
            if (tables.recurringOccurrences.isNotEmpty()) {
                dao.insertOccurrences(tables.recurringOccurrences.map { it.toEntity() })
            }
            if (tables.exchangeRateSnapshots.isNotEmpty()) {
                dao.insertRateSnapshots(tables.exchangeRateSnapshots.map { it.toEntity() })
            }
            if (tables.exchangeRateHistory.isNotEmpty()) {
                dao.insertRateHistory(tables.exchangeRateHistory.map { it.toEntity() })
            }
            if (tables.pendingDrafts.isNotEmpty()) dao.insertDrafts(tables.pendingDrafts.map { it.toEntity() })
        }
    }
}
