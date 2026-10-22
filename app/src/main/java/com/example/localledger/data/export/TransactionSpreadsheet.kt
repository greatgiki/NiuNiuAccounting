package com.example.localledger.data.export

import com.example.localledger.data.local.model.TransactionDetailRow
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TransactionExportFormatter {
    val headers: List<String> = listOf(
        "时间",
        "类型",
        "金额",
        "币种",
        "账户",
        "转入账户",
        "转入金额",
        "转入币种",
        "分类",
        "备注",
        "本位币金额",
        "本位币",
    )

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun cells(row: TransactionDetailRow, zone: ZoneId, ledgerCurrency: String): List<String> {
        val transaction = row.transaction
        val type = when (transaction.type) {
            TransactionType.EXPENSE -> "支出"
            TransactionType.INCOME -> "收入"
            TransactionType.TRANSFER -> "转账"
        }
        return listOf(
            Instant.ofEpochMilli(transaction.transactionAt).atZone(zone).format(timeFormatter),
            type,
            plainAmount(transaction.amountMinor, row.accountCurrencyCode),
            row.accountCurrencyCode,
            row.accountName,
            row.toAccountName.orEmpty(),
            transaction.toAmountMinor?.let { plainAmount(it, row.toAccountCurrencyCode ?: row.accountCurrencyCode) }.orEmpty(),
            if (transaction.toAmountMinor == null) "" else row.toAccountCurrencyCode ?: row.accountCurrencyCode,
            row.categoryName.orEmpty(),
            transaction.note.orEmpty(),
            plainAmount(transaction.baseAmountMinor, ledgerCurrency),
            ledgerCurrency,
        )
    }

    fun plainAmount(amountMinor: Long, currencyCode: String): String {
        val digits = if (CurrencyCatalog.contains(currencyCode)) {
            CurrencyCatalog.of(currencyCode).minorDigits
        } else {
            2
        }
        val negative = amountMinor < 0
        val abs = BigInteger.valueOf(amountMinor).abs()
        val factor = BigInteger.TEN.pow(digits)
        val major = if (digits == 0) abs else abs.divide(factor)
        val fraction = if (digits == 0) "" else {
            val remainder = abs.remainder(factor).toInt()
            "." + remainder.toString().padStart(digits, '0')
        }
        return (if (negative) "-" else "") + major.toString() + fraction
    }
}

object TransactionSpreadsheet {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun csv(rows: List<List<String>>): ByteArray {
        val body = buildString {
            append(line(TransactionExportFormatter.headers))
            rows.forEach { append(line(it)) }
        }
        val encoded = body.toByteArray(StandardCharsets.UTF_8)
        return utf8Bom + encoded
    }

    fun xlsx(rows: List<List<String>>): ByteArray {
        val sheet = buildSheet(listOf(TransactionExportFormatter.headers) + rows)
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("xl/workbook.xml", WORKBOOK)
            zip.entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.entry("xl/styles.xml", STYLES)
            zip.entry("xl/worksheets/sheet1.xml", sheet)
        }
        return output.toByteArray()
    }

    private fun line(cells: List<String>): String =
        cells.joinToString(separator = ",") { csvCell(it) } + "\r\n"

    private fun csvCell(value: String): String {
        val guarded = if (looksLikeFormula(value)) "'$value" else value
        val escaped = guarded.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun buildSheet(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, cells ->
            val rowNumber = rowIndex + 1
            append("<row r=\"$rowNumber\">")
            cells.forEachIndexed { column, value ->
                val ref = columnName(column) + rowNumber
                append("<c r=\"$ref\" t=\"inlineStr\"><is><t")
                if (value.needsPreserve()) append(" xml:space=\"preserve\"")
                append(">")
                append(xml(value))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val chars = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            chars.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return chars.reverse().toString()
    }

    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> if (char.code >= 0x20 || char == '\n' || char == '\t') append(char)
            }
        }
    }

    private fun String.needsPreserve(): Boolean =
        startsWith(' ') || endsWith(' ') || contains('\n')

    private fun ZipOutputStream.entry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun looksLikeFormula(value: String): Boolean {
        val first = value.firstOrNull() ?: return false
        if (first == '=' || first == '+' || first == '@') return true
        if (first != '-') return false
        val second = value.drop(1).firstOrNull() ?: return true
        return !second.isDigit()
    }

    private val CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private val ROOT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private val WORKBOOK = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="流水" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private val WORKBOOK_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private val STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
          <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
        </styleSheet>
    """.trimIndent()
}
