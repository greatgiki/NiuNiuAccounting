package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType
import java.time.ZoneId

object BillFileParser {
    private const val MAX_BYTES = 20 * 1024 * 1024

    private enum class Column {
        TIME,
        TYPE_LABEL,
        COUNTERPARTY,
        GOODS,
        DIRECTION,
        AMOUNT,
        PAYMENT_METHOD,
        STATUS,
        EXTERNAL_ID,
        MERCHANT_ID,
        NOTE,
    }

    private val aliases: Map<String, Column> = mapOf(
        "交易时间" to Column.TIME,
        "交易类型" to Column.TYPE_LABEL,
        "交易分类" to Column.TYPE_LABEL,
        "交易对方" to Column.COUNTERPARTY,
        "商品" to Column.GOODS,
        "商品说明" to Column.GOODS,
        "商品名称" to Column.GOODS,
        "收/支" to Column.DIRECTION,
        "收支" to Column.DIRECTION,
        "金额(元)" to Column.AMOUNT,
        "金额" to Column.AMOUNT,
        "支付方式" to Column.PAYMENT_METHOD,
        "收/付款方式" to Column.PAYMENT_METHOD,
        "收付款方式" to Column.PAYMENT_METHOD,
        "当前状态" to Column.STATUS,
        "交易状态" to Column.STATUS,
        "交易单号" to Column.EXTERNAL_ID,
        "交易订单号" to Column.EXTERNAL_ID,
        "商户单号" to Column.MERCHANT_ID,
        "商家订单号" to Column.MERCHANT_ID,
        "备注" to Column.NOTE,
    )

    fun parse(bytes: ByteArray, fileName: String, zoneId: ZoneId): BillParseResult {
        if (bytes.isEmpty()) throw BillParseException("文件是空的。")
        if (bytes.size > MAX_BYTES) throw BillParseException("文件超过 20MB，请缩小导出时间范围后再试。")
        if (MinimalXlsxReader.isZip(bytes)) {
            if (!MinimalXlsxReader.isXlsx(bytes)) {
                throw BillParseException("这是压缩包。微信邮件里的账单通常有密码，请先解压，再选择里面的 csv 或 xlsx 文件。")
            }
            return parseTable(MinimalXlsxReader.read(bytes), fileName, zoneId)
        }
        val text = DelimitedTable.decode(bytes)
        return parseTable(DelimitedTable.parse(text), fileName, zoneId)
    }

    fun parseTable(table: List<List<String>>, fileName: String, zoneId: ZoneId): BillParseResult {
        val headerIndex = table.indexOfFirst { isHeader(it) }
        if (headerIndex < 0) {
            throw BillParseException("没有找到表头。请选择微信支付或支付宝导出的账单，而不是加密压缩包。")
        }
        val header = table[headerIndex]
        val columns = mapColumns(header)
        if (Column.TIME !in columns || Column.DIRECTION !in columns || Column.AMOUNT !in columns) {
            throw BillParseException("表头缺少交易时间、收/支或金额，无法导入。")
        }
        val source = detectSource(header, fileName)
        val rows = mutableListOf<ParsedBillRow>()
        val skipped = mutableListOf<SkippedBillRow>()
        for (index in headerIndex + 1 until table.size) {
            val cells = table[index]
            if (cells.all { it.isBlank() }) continue
            if (isFooter(cells)) break
            when (val parsed = parseRow(source, columns, cells, zoneId)) {
                is RowOutcome.Keep -> rows += parsed.row
                is RowOutcome.Skip -> skipped += parsed.skipped
            }
        }
        if (rows.isEmpty() && skipped.isEmpty()) {
            throw BillParseException("账单里没有可识别的流水。")
        }
        return BillParseResult(source, rows, skipped)
    }

    private fun parseRow(
        source: BillSource,
        columns: Map<Column, Int>,
        cells: List<String>,
        zoneId: ZoneId,
    ): RowOutcome {
        val timeText = cell(cells, columns, Column.TIME)
        val direction = cell(cells, columns, Column.DIRECTION)
        val amountText = cell(cells, columns, Column.AMOUNT)
        val typeLabel = cell(cells, columns, Column.TYPE_LABEL)
        val counterparty = clean(cell(cells, columns, Column.COUNTERPARTY))
        val goods = clean(cell(cells, columns, Column.GOODS))
        val paymentMethod = clean(cell(cells, columns, Column.PAYMENT_METHOD))
        val status = clean(cell(cells, columns, Column.STATUS))
        val externalId = clean(cell(cells, columns, Column.EXTERNAL_ID))
        val merchantId = clean(cell(cells, columns, Column.MERCHANT_ID))
        val note = clean(cell(cells, columns, Column.NOTE))
        val summary = listOf(timeText, counterparty, goods, amountText).filter { it.isNotBlank() }.joinToString(" ")

        if (timeText.isBlank() && direction.isBlank() && amountText.isBlank()) {
            return RowOutcome.Skip(SkippedBillRow(RowSkipReason.EMPTY, summary))
        }
        val transactionAt = BillTimes.parse(timeText, zoneId)
            ?: return RowOutcome.Skip(SkippedBillRow(RowSkipReason.INVALID_TIME, summary.ifBlank { timeText }))
        if (isFailedOrClosed(status)) {
            return RowOutcome.Skip(SkippedBillRow(RowSkipReason.FAILED_OR_CLOSED, summary))
        }
        val type = classify(direction, typeLabel, goods, status)
            ?: return RowOutcome.Skip(SkippedBillRow(RowSkipReason.NEUTRAL, summary))
        val amountMinor = BillAmounts.parseYuanToMinor(amountText)
            ?: return RowOutcome.Skip(SkippedBillRow(RowSkipReason.INVALID_AMOUNT, summary))
        return RowOutcome.Keep(
            ParsedBillRow(
                source = source,
                type = type,
                amountMinor = amountMinor,
                transactionAt = transactionAt,
                counterparty = counterparty,
                goods = goods,
                paymentMethod = paymentMethod,
                status = status,
                externalId = externalId,
                merchantOrderId = merchantId,
                note = note,
                keywordText = listOf(counterparty, goods, typeLabel, note).filter { it.isNotBlank() }.joinToString(" "),
            ),
        )
    }

    internal fun classify(direction: String, typeLabel: String, goods: String, status: String): TransactionType? {
        val dir = direction.trim()
        if (dir.contains("支出")) return TransactionType.EXPENSE
        if (dir.contains("收入")) return TransactionType.INCOME
        if (!isNeutral(dir)) return null
        val typeSaysRefund = typeLabel.contains("退款") || goods.contains("退款")
        val statusSaysRefund = status.contains("退款成功") || status.contains("已退款") || status.contains("已全额退款")
        val looksLikeOriginalPurchase = typeLabel.contains("商户") || typeLabel.contains("消费") || typeLabel.contains("转账")
        return if (typeSaysRefund || (statusSaysRefund && !looksLikeOriginalPurchase)) {
            TransactionType.INCOME
        } else {
            null
        }
    }

    internal fun isFailedOrClosed(status: String): Boolean {
        val text = status.trim()
        if (text.isEmpty() || isNeutral(text)) return false
        if (text.contains("退款成功") || text.contains("已全额退款") || text.contains("已部分退款") || text == "已退款") {
            return false
        }
        return listOf("失败", "关闭", "取消", "撤销", "未付款", "待付款", "等待付款", "待支付", "未支付")
            .any { text.contains(it) }
    }

    private fun isNeutral(direction: String): Boolean {
        val text = direction.trim()
        return text.isEmpty() || text == "/" || text == "／" || text == "-" || text == "--" || text == "不计收支" || text == "无"
    }

    private fun isHeader(cells: List<String>): Boolean {
        val names = cells.map { normalizeHeader(it) }.toSet()
        return "交易时间" in names && ("收/支" in names || "收支" in names)
    }

    private fun mapColumns(header: List<String>): Map<Column, Int> {
        val mapped = linkedMapOf<Column, Int>()
        header.forEachIndexed { index, raw ->
            val key = aliases[normalizeHeader(raw)] ?: return@forEachIndexed
            mapped.putIfAbsent(key, index)
        }
        return mapped
    }

    internal fun detectSource(header: List<String>, fileName: String): BillSource {
        val names = header.map { normalizeHeader(it) }.toSet()
        val alipay = listOf("交易订单号", "收/付款方式", "交易分类", "对方账号", "商家订单号").count { it in names }
        val wechat = listOf("交易单号", "支付方式", "当前状态", "交易类型", "商户单号").count { it in names }
        if (alipay > wechat) return BillSource.ALIPAY
        if (wechat > alipay) return BillSource.WECHAT
        val name = fileName.lowercase()
        return when {
            "支付宝" in fileName || "alipay" in name -> BillSource.ALIPAY
            "微信" in fileName || "wechat" in name || "weixin" in name -> BillSource.WECHAT
            alipay > 0 -> BillSource.ALIPAY
            wechat > 0 -> BillSource.WECHAT
            else -> throw BillParseException("无法识别这是微信还是支付宝账单。")
        }
    }

    internal fun normalizeHeader(value: String): String {
        return value.trim()
            .removePrefix("\uFEFF")
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u3000", "")
            .replace("（", "(")
            .replace("）", ")")
    }

    private fun isFooter(cells: List<String>): Boolean {
        val text = cells.joinToString("").trim()
        if (text.isEmpty()) return false
        return text.startsWith("---") ||
            text.startsWith("——") ||
            text.startsWith("已收入") ||
            text.startsWith("已支出") ||
            (text.contains("共") && text.contains("笔")) ||
            text.contains("账单明细仅供")
    }

    private fun cell(cells: List<String>, columns: Map<Column, Int>, column: Column): String {
        val index = columns[column] ?: return ""
        return cells.getOrNull(index).orEmpty().trim()
    }

    private fun clean(value: String): String {
        val text = value.trim()
        return if (text == "/" || text == "／") "" else text
    }

    private sealed interface RowOutcome {
        data class Keep(val row: ParsedBillRow) : RowOutcome
        data class Skip(val skipped: SkippedBillRow) : RowOutcome
    }
}
