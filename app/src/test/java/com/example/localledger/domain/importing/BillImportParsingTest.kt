package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillImportParsingTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun wechatCsvSkipsPreambleFailuresAndNeutralTransfers() {
        val parsed = BillFileParser.parse(resource("bills/wechat_bill.csv"), "微信支付账单流水文件.csv", zone)
        assertEquals(BillSource.WECHAT, parsed.source)
        assertEquals(listOf("4200000000000000001", "4200000000000000002", "4200000000000000003", "5010000000000000001", "1000050000001", "5010000000000000002"), parsed.rows.map { it.externalId })
        assertEquals(TransactionType.EXPENSE, parsed.rows[0].type)
        assertEquals(2_850L, parsed.rows[0].amountMinor)
        assertEquals("美团", parsed.rows[0].counterparty)
        assertEquals(TransactionType.INCOME, parsed.rows[3].type)
        assertEquals(1_000L, parsed.rows[3].amountMinor)
        assertEquals(TransactionType.INCOME, parsed.rows[5].type)
        assertEquals(500L, parsed.rows[5].amountMinor)
        assertTrue(parsed.skipped.any { it.reason == RowSkipReason.NEUTRAL })
        assertTrue(parsed.skipped.any { it.reason == RowSkipReason.FAILED_OR_CLOSED })
        assertEquals(
            LocalDate.of(2024, 3, 2),
            Instant.ofEpochMilli(parsed.rows[0].transactionAt).atZone(zone).toLocalDate(),
        )
    }

    @Test
    fun alipayGbkCsvSkipsFooterAndClosedRows() {
        val parsed = BillFileParser.parse(resource("bills/alipay_bill.csv"), "支付宝交易明细.csv", zone)
        assertEquals(BillSource.ALIPAY, parsed.source)
        assertEquals(listOf("饿了么", "地铁", "京东", "饿了么", "公司"), parsed.rows.map { it.counterparty })
        assertEquals(listOf(3_280L, 400L, 1_990L, 600L, 100_000L), parsed.rows.map { it.amountMinor })
        assertEquals(TransactionType.INCOME, parsed.rows[3].type)
        assertEquals(TransactionType.INCOME, parsed.rows[4].type)
        assertTrue(parsed.skipped.any { it.reason == RowSkipReason.FAILED_OR_CLOSED })
        assertTrue(parsed.rows.none { it.externalId == "2024030612000001" })
    }

    @Test
    fun wechatXlsxReadsSharedStringsAndNumericCells() {
        val parsed = BillFileParser.parse(resource("bills/wechat_bill.xlsx"), "微信支付账单流水文件.xlsx", zone)
        assertEquals(BillSource.WECHAT, parsed.source)
        assertEquals(2, parsed.rows.size)
        assertEquals("美团", parsed.rows[0].counterparty)
        assertEquals(1_250L, parsed.rows[0].amountMinor)
        assertEquals(850L, parsed.rows[1].amountMinor)
        assertEquals(
            LocalDate.of(2024, 1, 1),
            Instant.ofEpochMilli(parsed.rows[1].transactionAt).atZone(zone).toLocalDate(),
        )
        assertTrue(parsed.skipped.any { it.reason == RowSkipReason.FAILED_OR_CLOSED })
    }

    @Test
    fun headersCanBeReorderedAndUseFullwidthParentheses() {
        val table = listOf(
            listOf("说明", "不是表头"),
            listOf("备注", "金额（元）", "当前状态", "交易单号", "收/支", "交易对方", "交易时间", "支付方式", "商品", "交易类型", "商户单号"),
            listOf("午饭", "¥12.50", "支付成功", "wx-1", "支出", "美团", "2024-05-01 12:00:00", "零钱", "外卖", "商户消费", "m-1"),
        )
        val parsed = BillFileParser.parseTable(table, "账单.csv", zone)
        assertEquals(BillSource.WECHAT, parsed.source)
        assertEquals(1_250L, parsed.rows.single().amountMinor)
        assertEquals("美团", parsed.rows.single().counterparty)
        assertEquals("wx-1", parsed.rows.single().externalId)
    }

    @Test
    fun plainZipAsksTheUserToUnzipFirst() {
        val zip = java.io.ByteArrayOutputStream().use { output ->
            java.util.zip.ZipOutputStream(output).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zip.write("not a bill".toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
        val error = runCatching { BillFileParser.parse(zip, "微信支付账单.zip", zone) }.exceptionOrNull()
        assertTrue(error is BillParseException)
        assertTrue(error?.message.orEmpty().contains("解压"))
    }

    @Test
    fun columnLettersAndAmounts() {
        assertEquals(0, MinimalXlsxReader.columnIndex("A1"))
        assertEquals(26, MinimalXlsxReader.columnIndex("AA12"))
        assertEquals(1_234_50L, BillAmounts.parseYuanToMinor("¥1,234.50"))
        assertNull(BillAmounts.parseYuanToMinor("/"))
    }

    private fun resource(path: String): ByteArray {
        return javaClass.classLoader!!.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("missing fixture $path")
    }
}
