package com.example.localledger.domain.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object DelimitedTable {
    private val gbk: Charset = Charset.forName("GBK")

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = decodeStrict(bytes, Charsets.UTF_8)
        if (utf8 != null && looksLikeBill(utf8)) return utf8
        val decodedGbk = String(bytes, gbk)
        if (utf8 == null) return decodedGbk
        if (looksLikeBill(decodedGbk) && !looksLikeBill(utf8)) return decodedGbk
        return utf8
    }

    fun looksLikeBill(text: String): Boolean {
        return text.contains("交易时间") || text.contains("微信支付") || text.contains("支付宝")
    }

    fun parse(text: String): List<List<String>> {
        val delimiter = detectDelimiter(text)
        return parseDelimited(text, delimiter)
    }

    fun detectDelimiter(text: String): Char {
        val header = text.lineSequence().firstOrNull { line ->
            val compact = line.replace(" ", "").replace("\uFEFF", "")
            compact.contains("交易时间") && (compact.contains("收/支") || compact.contains("收支"))
        } ?: return ','
        val tabs = header.count { it == '\t' }
        val commas = header.count { it == ',' }
        return if (tabs > commas) '\t' else ','
    }

    fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i += 1
                    } else {
                        inQuotes = false
                    }
                }
                ch == '"' && cell.isEmpty() -> inQuotes = true
                ch == delimiter && !inQuotes -> {
                    row += cell.toString()
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i += 1
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(ch)
            }
            i += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
