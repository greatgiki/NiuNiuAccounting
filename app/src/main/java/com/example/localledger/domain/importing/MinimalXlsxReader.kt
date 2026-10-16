package com.example.localledger.domain.importing

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the first worksheet of an Office Open XML workbook.
 * Only shared strings, inline strings and numeric cells are required for bill exports.
 */
object MinimalXlsxReader {
    fun read(bytes: ByteArray): List<List<String>> {
        val entries = readZip(bytes)
        val sheetPath = entries.keys.firstOrNull { it.equals("xl/worksheets/sheet1.xml", ignoreCase = true) }
            ?: entries.keys.firstOrNull { path ->
                val name = path.substringAfterLast('/')
                path.contains("worksheets/", ignoreCase = true) &&
                    name.startsWith("sheet", ignoreCase = true) &&
                    name.endsWith(".xml", ignoreCase = true)
            }
            ?: throw BillParseException("表格里没有工作表，请确认这是微信支付导出的 xlsx。")
        val sharedPath = entries.keys.firstOrNull { it.equals("xl/sharedStrings.xml", ignoreCase = true) }
        val shared = sharedPath?.let { readSharedStrings(entries.getValue(it)) }.orEmpty()
        return readSheet(entries.getValue(sheetPath), shared)
    }

    fun isXlsx(bytes: ByteArray): Boolean {
        if (!isZip(bytes)) return false
        return try {
            readZip(bytes).keys.any { it.equals("xl/workbook.xml", ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    fun isZip(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        map[entry.name.trimStart('/')] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (error: Exception) {
            throw BillParseException("无法读取压缩包。微信邮件里的账单通常有密码，请先解压，再选择里面的表格文件。")
        }
        if (map.isEmpty()) {
            throw BillParseException("文件是空的压缩包。请先解压微信账单，再选择里面的表格文件。")
        }
        return map
    }

    private fun readSharedStrings(xml: ByteArray): List<String> {
        val parser = parser(xml)
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inItem = true
                        current.clear()
                    }
                    "t" -> if (inItem) inText = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> {
                        values += current.toString()
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return values
    }

    private fun readSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val parser = parser(xml)
        val rows = mutableListOf<List<String>>()
        val cells = sortedMapOf<Int, String>()
        var column = -1
        var nextColumn = 0
        var cellType: String? = null
        var inValue = false
        var inInline = false
        val value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        cells.clear()
                        nextColumn = 0
                    }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r")
                        column = columnIndex(ref).takeIf { it >= 0 } ?: nextColumn
                        nextColumn = column + 1
                        cellType = parser.getAttributeValue(null, "t")
                        value.clear()
                        inValue = false
                        inInline = false
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInline = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inValue || inInline) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInline = false
                    "c" -> {
                        if (column >= 0) cells[column] = cellText(cellType, value.toString(), shared)
                    }
                    "row" -> if (cells.isNotEmpty()) rows += materialize(cells)
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun cellText(type: String?, raw: String, shared: List<String>): String {
        return when (type) {
            "s" -> shared.getOrElse(raw.toIntOrNull() ?: -1) { "" }
            "inlineStr", "str" -> raw
            "b" -> if (raw == "1") "TRUE" else "FALSE"
            else -> raw
        }
    }

    private fun materialize(cells: Map<Int, String>): List<String> {
        if (cells.isEmpty()) return emptyList()
        val last = cells.keys.maxOrNull() ?: return emptyList()
        return List(last + 1) { index -> cells[index].orEmpty() }
    }

    internal fun columnIndex(ref: String?): Int {
        if (ref.isNullOrBlank()) return -1
        var index = 0
        var seen = false
        for (ch in ref) {
            if (ch.isLetter()) {
                seen = true
                index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return if (seen) index - 1 else -1
    }

    private fun parser(xml: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(xml), "UTF-8")
        }
    }
}
