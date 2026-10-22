package com.example.localledger.data.export

import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.data.local.model.TransactionDetailRow
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream

class TransactionSpreadsheetTest {
    @Test
    fun csvUsesUtf8BomChineseHeadersAndEscapesNotes() {
        val bytes = TransactionSpreadsheet.csv(listOf(sampleCells()))
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = text.trimEnd().split("\r\n")
        assertEquals("时间,类型,金额,币种,账户,转入账户,转入金额,转入币种,分类,备注,本位币金额,本位币", lines[0])
        assertTrue(lines[1].startsWith("2026-10-21 09:30,支出,12.50,CNY,现金,,,,餐饮,"))
        assertTrue(lines[1].contains("\"午饭, \"\"好\"\"\""))
        assertTrue(text.contains("12.50"))
        assertFalse(text.contains("¥"))
    }

    @Test
    fun csvDoesNotTreatNegativeAmountsAsFormulas() {
        val row = sampleCells().toMutableList()
        row[2] = "-12.50"
        row[9] = "=cmd"
        val text = TransactionSpreadsheet.csv(listOf(row)).toString(Charsets.UTF_8)
        assertTrue(text.contains(",-12.50,"))
        assertTrue(text.contains("'=cmd"))
    }

    @Test
    fun xlsxContainsChineseHeadersAndValues() {
        val bytes = TransactionSpreadsheet.xlsx(listOf(sampleCells()))
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("时间"))
        assertTrue(sheet.contains("本位币"))
        assertTrue(sheet.contains("2026-10-21 09:30"))
        assertTrue(sheet.contains("支出"))
        assertTrue(sheet.contains("12.50"))
        assertTrue(sheet.contains("午饭"))
        assertTrue(entries.getValue("xl/workbook.xml").contains("流水"))
    }

    private fun sampleCells(): List<String> {
        val zone = ZoneId.of("Asia/Shanghai")
        val at = LocalDateTime.of(2026, 10, 21, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val row = TransactionDetailRow(
            transaction = TransactionEntity(
                id = "tx",
                ledgerId = "ledger",
                type = TransactionType.EXPENSE,
                amountMinor = 1_250,
                accountId = "cash",
                toAccountId = null,
                categoryId = "food",
                transactionAt = at,
                note = "午饭, \"好\"",
                createdAt = at,
                updatedAt = at,
                baseAmountMinor = 1_250,
                fxRate = "1",
            ),
            accountName = "现金",
            toAccountName = null,
            categoryName = "餐饮",
            categoryIconKey = "food",
            accountCurrencyCode = "CNY",
            toAccountCurrencyCode = null,
        )
        return TransactionExportFormatter.cells(row, zone, "CNY")
    }
}
