package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillAmounts

/**
 * Which number on a payment page is the amount that moved.
 *
 * Alipay top-up and bill pages also show the balance after the payment
 * (卡内余额, 话费余额, 可用余额) and the price before a discount. Those must
 * not win just because they appear first. A labeled paid amount wins, then a
 * prominent `-¥` amount. If more than one payment-like number is left, the
 * sheet keeps the preferred candidate and asks the user to confirm it.
 */
object PaymentAmounts {
    data class Reading(
        val amountMinor: Long,
        val uncertain: Boolean,
        val choices: List<Long>,
        val note: String,
    )

    private const val TIER_IGNORED = 0
    private const val TIER_PAID = 1
    private const val TIER_SIGNED = 2
    private const val TIER_ORDER = 3
    private const val TIER_GENERIC = 4
    private const val TIER_PLAIN = 5

    private val token = Regex(
        """([+＋\-－−])?\s*([¥￥]\s*)?([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(元)?""",
    )

    fun read(lines: List<String>): Reading? {
        val hits = collect(lines)
        if (hits.isEmpty()) return null
        val usable = hits.filter { it.tier != TIER_IGNORED }
        if (usable.isEmpty()) {
            val ignored = hits.map { it.minor }.distinct()
            return Reading(
                amountMinor = ignored.first(),
                uncertain = true,
                choices = ignored,
                note = "页面上只看到余额、优惠或积分，没有明确的支付金额。请改成实际付出的数字。",
            )
        }
        val bestTier = usable.minOf { it.tier }
        val best = usable.filter { it.tier == bestTier }.map { it.minor }.distinct()
        val others = usable.map { it.minor }.distinct()
        val chosen = best.first()
        val uncertain = best.size > 1 || (bestTier >= TIER_SIGNED && others.size > 1)
        val choices = listOf(chosen) + others.filter { it != chosen }
        val note = when {
            !uncertain -> ""
            best.size > 1 -> "页面上有多个可能的金额，请点选实际支付的数字。"
            else -> "页面上还有别的金额。请确认这笔是不是实际支付的。"
        }
        return Reading(chosen, uncertain, choices, note)
    }

    fun yuanText(amountMinor: Long): String {
        val abs = amountMinor.coerceAtLeast(0L)
        return "%d.%02d".format(abs / 100, abs % 100)
    }

    fun isSkippableCaption(text: String): Boolean {
        if (text.length > 24) return false
        val withoutWallet = text.replace("余额宝", "")
        if ("余额宝" in text && ignored(withoutWallet).not()) return false
        return ignored(text)
    }

    private fun collect(lines: List<String>): List<Hit> {
        val hits = ArrayList<Hit>()
        lines.forEachIndexed { index, raw ->
            if (skip(raw)) return@forEachIndexed
            val line = normalize(raw)
            val tokens = tokens(line)
            if (tokens.isEmpty()) return@forEachIndexed
            val pure = tokens.size == 1 && leftover(line, tokens).isEmpty()
            val previous = lines.getOrNull(index - 1)?.let(::normalize).orEmpty()
            tokens.forEachIndexed { tokenIndex, found ->
                val prefixStart = if (tokenIndex == 0) 0 else tokens[tokenIndex - 1].end
                val prefix = line.substring(prefixStart, found.start).trim()
                val context = when {
                    prefix.isNotEmpty() -> prefix
                    pure && moneyLabel(previous) -> previous
                    else -> ""
                }
                hits += Hit(minor = found.minor, tier = tier(found.negative, context))
            }
        }
        return hits
    }

    private fun tier(negative: Boolean, context: String): Int {
        if (ignored(context)) return TIER_IGNORED
        if (PaymentPageRules.paidAmountLabels.any { label -> label in context }) return TIER_PAID
        if (negative) return TIER_SIGNED
        if (PaymentPageRules.orderAmountLabels.any { label -> label in context }) return TIER_ORDER
        if (genericAmount(context)) return TIER_GENERIC
        return TIER_PLAIN
    }

    private fun moneyLabel(text: String): Boolean {
        if (text.isBlank()) return false
        if (ignored(text)) return true
        if (PaymentPageRules.paidAmountLabels.any { label -> label in text }) return true
        if (PaymentPageRules.orderAmountLabels.any { label -> label in text }) return true
        return genericAmount(text)
    }

    private fun genericAmount(text: String): Boolean {
        val trimmed = text.trim().trim(':', '：', ' ')
        if (trimmed.isEmpty() || ignored(trimmed)) return false
        return trimmed == "金额"
    }

    private fun ignored(text: String): Boolean {
        if (text.isBlank()) return false
        val withoutWallet = text.replace("余额宝", "")
        return PaymentPageRules.ignoredAmountWords.any { word -> word in withoutWallet }
    }

    private fun tokens(line: String): List<Token> {
        return token.findAll(line).mapNotNull { match ->
            val sign = match.groupValues[1]
            val currency = match.groupValues[2]
            val number = match.groupValues[3]
            val yuan = match.groupValues[4]
            val money = sign.isNotEmpty() || currency.isNotEmpty() || yuan.isNotEmpty() || '.' in number
            if (!money || number.isEmpty()) return@mapNotNull null
            val minor = BillAmounts.parseYuanToMinor(number) ?: return@mapNotNull null
            Token(
                start = match.range.first,
                end = match.range.last + 1,
                minor = minor,
                negative = sign.any { it == '-' || it == '－' || it == '−' },
            )
        }.toList()
    }

    private fun leftover(line: String, tokens: List<Token>): String {
        val token = tokens.single()
        return (line.substring(0, token.start) + line.substring(token.end))
            .replace(" ", "")
            .replace("\u00A0", "")
    }

    private fun skip(line: String): Boolean {
        if ("年" in line && "月" in line) return true
        val compact = line.replace(" ", "")
        if (':' in compact && "¥" !in compact && "￥" !in compact && "元" !in compact) return true
        return false
    }

    private fun normalize(line: String): String {
        return line.replace("，", ",").replace("．", ".").replace("\u00A0", " ").trim()
    }

    private data class Token(
        val start: Int,
        val end: Int,
        val minor: Long,
        val negative: Boolean,
    )

    private data class Hit(
        val minor: Long,
        val tier: Int,
    )
}
