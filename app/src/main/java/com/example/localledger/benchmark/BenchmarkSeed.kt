package com.example.localledger.benchmark

import androidx.room.withTransaction
import com.example.localledger.BuildConfig
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.data.local.database.PresetData
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.importing.RecordOrigin
import com.example.localledger.domain.model.TransactionType
import java.util.UUID

/**
 * Demo rows for macrobenchmark only. Release and debug keep [BuildConfig.SEED_DEMO] false,
 * so R8 drops the call site. Rows use stable ids and are skipped once the ledger is full.
 */
object BenchmarkFlags {
    @Volatile
    var seedCount: Int = 0

    @Volatile
    var expandedTransactionList: Boolean = false
}

object BenchmarkSeed {
    const val EXTRA_SEED_COUNT = "com.example.localledger.SEED_COUNT"

    suspend fun seedIfRequested(databaseProvider: DatabaseProvider) {
        if (!BuildConfig.SEED_DEMO) return
        val count = BenchmarkFlags.seedCount
        if (count <= 0) return
        val database = databaseProvider.get()
        val ledgerId = PresetData.DEFAULT_LEDGER_ID
        if (database.transactionDao().countInLedger(ledgerId) >= count) return
        val now = System.currentTimeMillis()
        val accountId = stableId(ledgerId, "account", 0)
        val categoryId = stableId(ledgerId, "category-expense", 0)
        database.withTransaction {
            repeat(count) { index ->
                database.transactionDao().upsert(
                    TransactionEntity(
                        id = "bench-$index",
                        ledgerId = ledgerId,
                        type = TransactionType.EXPENSE,
                        amountMinor = 1_200L + (index % 40) * 100L,
                        accountId = accountId,
                        toAccountId = null,
                        categoryId = categoryId,
                        transactionAt = now - index * 3_600_000L,
                        note = "基准 $index",
                        createdAt = now,
                        updatedAt = now,
                        baseAmountMinor = 1_200L + (index % 40) * 100L,
                        fxRate = "1",
                        origin = RecordOrigin.MANUAL,
                    ),
                )
            }
        }
    }

    private fun stableId(ledgerId: String, kind: String, index: Int): String =
        UUID.nameUUIDFromBytes("$ledgerId:$kind:$index".toByteArray(Charsets.UTF_8)).toString()
}
