package com.example.localledger.data.export

import android.content.Context
import android.net.Uri
import com.example.localledger.data.backup.BackupFailure
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.domain.time.ZoneIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class SpreadsheetFormat { CSV, XLSX }

@Singleton
class TransactionExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: DatabaseProvider,
    private val zoneIdProvider: ZoneIdProvider,
) {
    suspend fun rows(ledgerId: String, start: LocalDate, end: LocalDate): List<List<String>> =
        withContext(Dispatchers.IO) {
            if (end.isBefore(start)) throw BackupFailure("结束日期不能早于开始日期")
            val zone = zoneIdProvider.current()
            val startInclusive = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endExclusive = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val database = databaseProvider.get()
            val ledger = database.ledgerDao().findById(ledgerId)
                ?: throw BackupFailure("找不到要导出的账本")
            database.transactionDao()
                .listDetailsInRange(ledgerId, startInclusive, endExclusive)
                .map { TransactionExportFormatter.cells(it, zone, ledger.currencyCode) }
        }

    suspend fun write(
        uri: Uri,
        format: SpreadsheetFormat,
        ledgerId: String,
        start: LocalDate,
        end: LocalDate,
    ): Int = withContext(Dispatchers.IO) {
        val exported = rows(ledgerId, start, end)
        val bytes = when (format) {
            SpreadsheetFormat.CSV -> TransactionSpreadsheet.csv(exported)
            SpreadsheetFormat.XLSX -> TransactionSpreadsheet.xlsx(exported)
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw BackupFailure("无法写入所选位置")
        exported.size
    }
}
