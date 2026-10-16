package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType

data class NotificationPattern(
    val id: String,
    val source: BillSource,
    val type: TransactionType,
    val regex: Regex,
    val amountGroup: Int = 1,
    val counterpartyGroup: Int? = null,
)

data class ParsedNotification(
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long,
    val counterparty: String,
    val patternId: String,
)

object PaymentNotificationPatterns {
    private val amount = """([0-9]+(?:\.[0-9]{1,2})?)"""
    private val money = """[¥￥]?\s*$amount\s*元?"""

    val all: List<NotificationPattern> = listOf(
        NotificationPattern(
            id = "wechat-receipt-arrived",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""微信支付收款到账\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-receipt-amount",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""收款到账\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-received",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""已收款\s*[¥￥]\s*$amount"""),
        ),
        NotificationPattern(
            id = "wechat-income-amount",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""收款金额\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-paid",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""微信支付[\s\S]{0,80}?已支付\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-pay-to",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""向(.{1,30}?)付款\s*$money"""),
            amountGroup = 2,
            counterpartyGroup = 1,
        ),
        NotificationPattern(
            id = "wechat-paid-short",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""(?:^|\n)已支付\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-paid",
            source = BillSource.ALIPAY,
            type = TransactionType.EXPENSE,
            regex = Regex("""(?:你已)?成功付款\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-pay-success",
            source = BillSource.ALIPAY,
            type = TransactionType.EXPENSE,
            regex = Regex("""付款成功\s*[，,]?\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-you-paid",
            source = BillSource.ALIPAY,
            type = TransactionType.EXPENSE,
            regex = Regex("""你已付款\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-received",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""(?:你已)?成功收款\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-receive-success",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""收款成功\s*[，,]?\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-brand-received",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""支付宝成功收款\s*$money"""),
        ),
    )

    private val counterpartyPatterns = listOf(
        Regex("""(?:收款方|付款方|商家|交易对方|对方)[:：]\s*([^\s，,。]{1,30})"""),
        Regex("""向([^\s，,。¥￥0-9]{1,30}?)(?:付款|支付)"""),
    )

    fun counterparty(text: String): String {
        return counterpartyPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        }.orEmpty()
    }
}

object PaymentNotificationParser {
    fun parse(source: BillSource, text: String): ParsedNotification? {
        val normalized = text.replace('\u00A0', ' ').trim()
        if (normalized.isEmpty()) return null
        val pattern = PaymentNotificationPatterns.all.firstOrNull { candidate ->
            candidate.source == source && candidate.regex.containsMatchIn(normalized)
        } ?: return null
        val match = pattern.regex.find(normalized) ?: return null
        val amountText = match.groupValues.getOrNull(pattern.amountGroup).orEmpty()
        val amount = BillAmounts.parseYuanToMinor(amountText) ?: return null
        val fromGroup = pattern.counterpartyGroup?.let { match.groupValues.getOrNull(it).orEmpty().trim() }.orEmpty()
        val counterparty = fromGroup.ifBlank { PaymentNotificationPatterns.counterparty(normalized) }
        return ParsedNotification(
            source = source,
            type = pattern.type,
            amountMinor = amount,
            counterparty = counterparty,
            patternId = pattern.id,
        )
    }
}
