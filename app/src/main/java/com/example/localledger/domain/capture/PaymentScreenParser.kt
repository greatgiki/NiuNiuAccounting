package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.TransactionType
import java.time.ZoneId

enum class CapturePage { SUCCESS, DETAIL }

data class ParsedCapture(
    val source: BillSource,
    val page: CapturePage,
    val type: TransactionType,
    val amountMinor: Long,
    val merchant: String,
    val accountLabel: String,
    val occurredAt: Long,
    val ruleId: String,
    val amountUncertain: Boolean = false,
    val amountChoices: List<Long> = emptyList(),
    val amountNote: String = "",
)

object PaymentScreenParser {
    private val decimalLine = Regex(
        """^[+＋\-－]?(?:[¥￥]\s*)?([0-9]{1,7}(?:,[0-9]{3})*\.[0-9]{1,2})\s*元?$""",
    )
    private val currencyLine = Regex(
        """^[+＋\-－]?[¥￥]\s*([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元?$""",
    )
    private val yuanLine = Regex(
        """^[+＋\-－]?([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元$""",
    )

    fun parse(source: BillSource, root: ScreenNode, now: Long, zone: ZoneId): ParsedCapture? {
        if (source == BillSource.BANK) return null
        val lines = ScreenText.lines(root)
        if (lines.isEmpty()) return null
        val joined = lines.joinToString("\n")
        if (isPasswordPage(joined) || isChat(lines, joined) || isPending(joined)) return null
        val detail = PaymentPageRules.detailMarkers.any { marker -> marker in joined }
        val success = PaymentPageRules.successMarkers.any { marker -> marker in joined } ||
            PaymentPageRules.detailOkMarkers.any { marker -> marker in joined }
        if (!detail && !success) return null
        if (detail && !acceptDetail(joined)) return null
        if (!detail && PaymentPageRules.successMarkers.none { marker -> marker in joined }) return null
        val amount = PaymentAmounts.read(lines) ?: return null
        val page = if (detail) CapturePage.DETAIL else CapturePage.SUCCESS
        val merchant = merchant(lines).orEmpty()
        val account = labeled(lines, PaymentPageRules.accountLabels).orEmpty()
        val timeText = labeled(lines, PaymentPageRules.timeLabels).orEmpty()
        val occurredAt = PaymentTimes.parse(timeText.ifBlank { joined }, now, zone) ?: now
        return ParsedCapture(
            source = source,
            page = page,
            type = classify(joined),
            amountMinor = amount.amountMinor,
            merchant = merchant,
            accountLabel = account,
            occurredAt = occurredAt,
            ruleId = ruleId(source, page),
            amountUncertain = amount.uncertain,
            amountChoices = amount.choices,
            amountNote = amount.note,
        )
    }

    fun ruleId(source: BillSource, page: CapturePage): String {
        val app = when (source) {
            BillSource.WECHAT -> "wechat"
            BillSource.ALIPAY -> "alipay"
            BillSource.UNIONPAY -> "unionpay"
            BillSource.BANK -> "bank"
        }
        val kind = if (page == CapturePage.DETAIL) "bill-detail" else "pay-success"
        return "$app-$kind"
    }

    private fun acceptDetail(joined: String): Boolean {
        val failed = PaymentPageRules.failedMarkers.any { marker -> marker in joined }
        if (failed && "退款成功" !in joined) return false
        return PaymentPageRules.detailOkMarkers.any { marker -> marker in joined }
    }

    private fun isPasswordPage(joined: String): Boolean {
        val asks = listOf("支付密码", "请输入密码", "输入密码").any { phrase -> phrase in joined }
        if (!asks) return false
        val done = PaymentPageRules.successMarkers.any { marker -> marker in joined } ||
            PaymentPageRules.detailMarkers.any { marker -> marker in joined }
        return !done
    }

    private fun isChat(lines: List<String>, joined: String): Boolean {
        val strong = PaymentPageRules.successMarkers.any { marker -> marker in joined } ||
            PaymentPageRules.detailMarkers.any { marker -> marker in joined }
        if (PaymentPageRules.chatMarkers.any { marker -> marker in joined } && !strong) return true
        if (!strong && lines.size > 36 && lines.any { line -> line == "发送" || line == "发送消息" }) return true
        return false
    }

    private fun isPending(joined: String): Boolean {
        if (PaymentPageRules.pendingMarkers.none { marker -> marker in joined }) return false
        return PaymentPageRules.successMarkers.none { marker -> marker in joined }
    }

    private fun classify(joined: String): TransactionType {
        if ("对方已收钱" in joined && "转账" in joined) return TransactionType.TRANSFER
        if ("对方已收钱" in joined) return TransactionType.EXPENSE
        val transfer = PaymentPageRules.transferMarkers.any { marker -> marker in joined }
        val plainPay = "支付成功" in joined &&
            "转账成功" !in joined &&
            "充值" !in joined &&
            "提现" !in joined &&
            "还款" !in joined
        if (transfer && !plainPay) {
            if (PaymentPageRules.incomeMarkers.any { marker -> marker in joined }) return TransactionType.INCOME
            return TransactionType.TRANSFER
        }
        if (PaymentPageRules.incomeMarkers.any { marker -> marker in joined }) return TransactionType.INCOME
        return TransactionType.EXPENSE
    }

    private fun merchant(lines: List<String>): String? {
        labeled(lines, PaymentPageRules.merchantLabels)?.takeUnless { isNoise(it) }?.let { return it }
        return lines.firstOrNull { line -> !isNoise(line) }
    }

    private fun labeled(lines: List<String>, labels: List<String>): String? {
        lines.forEachIndexed { index, line ->
            for (label in labels) {
                if (line == label) {
                    return lines.getOrNull(index + 1)?.trim()?.takeIf { it.isNotEmpty() }
                }
                for (separator in arrayOf("：", ":")) {
                    val prefix = label + separator
                    if (line.startsWith(prefix)) {
                        return line.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
                    }
                }
                if (line.startsWith(label) && line.length > label.length + 1) {
                    val rest = line.removePrefix(label).trim(' ', ':', '：')
                    if (rest.isNotEmpty() && rest.length <= 40) return rest
                }
            }
        }
        return null
    }

    private fun isNoise(line: String): Boolean {
        val text = line.trim()
        if (text.length !in 2..32) return true
        if (PaymentPageRules.buttons.any { button -> text == button || text.startsWith(button) }) return true
        if (PaymentPageRules.successMarkers.any { marker -> marker in text } && text.length <= 8) return true
        if (PaymentPageRules.detailMarkers.any { marker -> marker == text }) return true
        if (PaymentPageRules.amountLabels.any { label -> text == label || text.startsWith(label) }) return true
        if (PaymentPageRules.accountLabels.any { label -> text == label || text.startsWith(label) }) return true
        if (PaymentPageRules.timeLabels.any { label -> text == label || text.startsWith(label) }) return true
        if (PaymentPageRules.merchantLabels.any { label -> text == label }) return true
        if (text in PaymentPageRules.statusWords) return true
        if (PaymentAmounts.isSkippableCaption(text)) return true
        val compact = text.replace(" ", "")
        if (decimalLine.matches(compact) || currencyLine.matches(compact) || yuanLine.matches(compact)) return true
        if (text.any { it.isDigit() } && text.count { it.isDigit() } * 2 >= text.length) return true
        return false
    }
}
