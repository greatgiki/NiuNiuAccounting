package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillAmounts
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.TransactionType
import java.time.ZoneId

/**
 * A WeChat「微信支付」official-account card inside「服务通知」, read only while it is on screen.
 * Plaintext amount and merchant only. A masked amount stays on the pending draft.
 */
object WeChatServiceNotice {
    private val voucherLines = listOf("支付凭证", "收款到账通知", "支付成功通知", "交易提醒")
    private val paidLabels = listOf("实付金额", "实付", "支付金额", "付款金额", "消费金额", "收款金额")
    private val merchantLabels = listOf("商户全称", "商户名称", "商户", "商品", "交易对方", "收款方", "付款方")
    private val accountLabels = listOf("支付方式", "付款方式", "收款方式")
    private val moneyLine = Regex(
        """^[+＋\-－]?(?:[¥￥]\s*)?([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元?$""",
    )
    private val yuanLine = Regex(
        """^[+＋\-－]?([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元$""",
    )

    fun parse(lines: List<String>, now: Long, zone: ZoneId): ParsedCapture? {
        if (lines.isEmpty()) return null
        val joined = lines.joinToString("\n")
        if ("微信支付" !in joined) return null
        val voucher = lines.any { line -> voucherLines.any { marker -> line == marker || line.startsWith(marker) } }
        val channel = "服务通知" in joined
        if (!voucher && !channel) return null
        if (PaymentPageRules.chatMarkers.any { marker -> marker in joined } && !voucher) return null
        if (PaymentPageRules.pendingMarkers.any { marker -> marker in joined } &&
            PaymentPageRules.successMarkers.none { marker -> marker in joined }
        ) {
            return null
        }
        if (PaymentPageRules.failedMarkers.any { marker -> marker in joined } && "退款" !in joined) return null
        val labeled = paidLabel(lines) ?: return if (voucher) loneAmount(lines, joined, now, zone) else null
        return capture(lines, joined, labeled.amount, labeled.income, now, zone)
    }

    private fun loneAmount(lines: List<String>, joined: String, now: Long, zone: ZoneId): ParsedCapture? {
        val amounts = lines.mapNotNull { line -> plainAmount(line) }
        if (amounts.size != 1) return null
        return capture(lines, joined, amounts.first(), income = income(joined), now, zone)
    }

    private fun capture(
        lines: List<String>,
        joined: String,
        amount: Long,
        income: Boolean,
        now: Long,
        zone: ZoneId,
    ): ParsedCapture {
        val account = labeled(lines, accountLabels).orEmpty()
        return ParsedCapture(
            source = BillSource.WECHAT,
            page = CapturePage.DETAIL,
            type = when {
                "转账成功" in joined || "转入零钱" in joined -> TransactionType.TRANSFER
                income || PaymentPageRules.incomeMarkers.any { marker -> marker in joined } -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            },
            amountMinor = amount,
            merchant = merchant(lines),
            accountLabel = account,
            occurredAt = PaymentTimes.parse(joined, now, zone) ?: now,
            ruleId = "wechat-service-notice",
        )
    }

    private data class LabeledAmount(val amount: Long, val income: Boolean)

    private fun paidLabel(lines: List<String>): LabeledAmount? {
        for (label in paidLabels) {
            val value = labeled(lines, listOf(label)) ?: continue
            if (isMasked(value) || "优惠" in value) continue
            val amount = BillAmounts.parseYuanToMinor(value) ?: continue
            val income = label.startsWith("收款")
            return LabeledAmount(amount, income)
        }
        return null
    }

    private fun plainAmount(line: String): Long? {
        if (isMasked(line) || "优惠" in line || "时间" in line || "年" in line || "月" in line) return null
        val compact = line.replace(" ", "").replace("\u00A0", "")
        val raw = moneyLine.matchEntire(compact)?.groupValues?.getOrNull(1)
            ?: yuanLine.matchEntire(compact)?.groupValues?.getOrNull(1)
            ?: return null
        return BillAmounts.parseYuanToMinor(raw)
    }

    private fun merchant(lines: List<String>): String {
        labeled(lines, merchantLabels)?.takeUnless { isNoise(it) }?.let { return it }
        return lines.firstOrNull { line -> !isNoise(line) }.orEmpty()
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
            }
        }
        return null
    }

    private fun income(joined: String): Boolean {
        return "收款" in joined || "退款" in joined || PaymentPageRules.incomeMarkers.any { marker -> marker in joined }
    }

    private fun isMasked(value: String): Boolean {
        return value.any { ch -> ch == '*' || ch == '＊' || ch == '∗' }
    }

    private fun isNoise(line: String): Boolean {
        val text = line.trim()
        if (text.length !in 2..32) return true
        if (text == "微信支付" || text == "服务通知") return true
        if (voucherLines.any { marker -> text == marker || text.startsWith(marker) }) return true
        if (paidLabels.any { label -> text == label || text.startsWith(label) }) return true
        if (accountLabels.any { label -> text == label || text.startsWith(label) }) return true
        if (merchantLabels.any { label -> text == label }) return true
        if (PaymentPageRules.buttons.any { button -> text == button }) return true
        if (isMasked(text)) return true
        val compact = text.replace(" ", "")
        if (moneyLine.matches(compact) || yuanLine.matches(compact)) return true
        if (text.any { it.isDigit() } && text.count { it.isDigit() } * 2 >= text.length) return true
        return false
    }
}
