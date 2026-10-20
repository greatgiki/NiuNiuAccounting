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

data class AmountlessPattern(
    val id: String,
    val source: BillSource,
    val type: TransactionType,
    val regex: Regex,
)

data class ParsedNotification(
    val source: BillSource,
    val type: TransactionType,
    val amountMinor: Long?,
    val counterparty: String,
    val patternId: String,
    val needsAmount: Boolean,
)

object PaymentNotificationPatterns {
    private val number = """([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)"""
    private val money = """[¥￥]?\s*$number\s*元?"""

    val all: List<NotificationPattern> = listOf(
        NotificationPattern(
            id = "wechat-receipt-arrived",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""微信支付收款到账\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-receipt-helper",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""微信支付收款\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-receipt-notice",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""收款到账通知\s*$money"""),
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
            regex = Regex("""已收款\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-income-amount",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""收款金额\s*:?\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-pay-to",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""向(.{1,40}?)付款\s*$money"""),
            amountGroup = 2,
            counterpartyGroup = 1,
        ),
        NotificationPattern(
            id = "wechat-pay-amount",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""付款金额\s*:?\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-paid",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""微信支付[\s\S]{0,80}?已支付\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-you-paid",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""(?:你)?已支付\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-voucher-amount",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""微信支付凭证\s*$money"""),
        ),
        NotificationPattern(
            id = "wechat-pay-success-amount",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""支付成功\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-scan-received",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""通过扫码向你付款\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-paid-you",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""向你付款\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-qr-received",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""收钱码收款(?:通知)?\s*$money"""),
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
            regex = Regex("""收款成功\s*[,，]?\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-brand-received",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""支付宝成功收款\s*$money"""),
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
            regex = Regex("""付款成功\s*[,，]?\s*$money"""),
        ),
        NotificationPattern(
            id = "alipay-you-paid",
            source = BillSource.ALIPAY,
            type = TransactionType.EXPENSE,
            regex = Regex("""你已付款\s*$money"""),
        ),
    )

    val amountless: List<AmountlessPattern> = listOf(
        AmountlessPattern(
            id = "wechat-income-no-amount",
            source = BillSource.WECHAT,
            type = TransactionType.INCOME,
            regex = Regex("""微信收款助手|微信支付收款|收款到账|收款金额|已收款"""),
        ),
        AmountlessPattern(
            id = "wechat-expense-no-amount",
            source = BillSource.WECHAT,
            type = TransactionType.EXPENSE,
            regex = Regex("""微信支付凭证|支付成功|付款金额|你已支付|已支付|向.{1,40}?付款"""),
        ),
        AmountlessPattern(
            id = "alipay-income-no-amount",
            source = BillSource.ALIPAY,
            type = TransactionType.INCOME,
            regex = Regex("""成功收款|收款成功|向你付款|通过扫码向你付款|收钱码"""),
        ),
        AmountlessPattern(
            id = "alipay-expense-no-amount",
            source = BillSource.ALIPAY,
            type = TransactionType.EXPENSE,
            regex = Regex("""成功付款|付款成功|你已付款|你已支付"""),
        ),
    )

    private val counterpartyPatterns = listOf(
        Regex("""(?:收款方|付款方|商家|交易对方|对方)[:：]\s*([^\s，,。]{1,30})"""),
        Regex("""向([^\s，,。¥￥0-9]{1,30}?)(?:付款|支付)"""),
    )

    fun counterparty(text: String): String {
        return counterpartyPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeUnless { it == "你" || it == "您" }
        }.orEmpty()
    }
}

object PaymentNotificationParser {
    private const val AMOUNTLESS_HEAD = 240

    fun parse(source: BillSource, text: String): ParsedNotification? {
        val normalized = NotificationText.normalize(text)
        if (normalized.isEmpty()) return null
        for (pattern in PaymentNotificationPatterns.all) {
            if (pattern.source != source) continue
            val match = pattern.regex.find(normalized) ?: continue
            val amountText = match.groupValues.getOrNull(pattern.amountGroup).orEmpty()
            val amount = BillAmounts.parseYuanToMinor(amountText) ?: continue
            val fromGroup = pattern.counterpartyGroup
                ?.let { match.groupValues.getOrNull(it).orEmpty().trim() }
                .orEmpty()
            val counterparty = fromGroup.ifBlank { PaymentNotificationPatterns.counterparty(normalized) }
            return ParsedNotification(
                source = source,
                type = pattern.type,
                amountMinor = amount,
                counterparty = counterparty,
                patternId = pattern.id,
                needsAmount = false,
            )
        }
        val head = normalized.take(AMOUNTLESS_HEAD)
        val amountless = PaymentNotificationPatterns.amountless.firstOrNull { candidate ->
            candidate.source == source && candidate.regex.containsMatchIn(head)
        } ?: return null
        return ParsedNotification(
            source = source,
            type = amountless.type,
            amountMinor = null,
            counterparty = PaymentNotificationPatterns.counterparty(normalized),
            patternId = amountless.id,
            needsAmount = true,
        )
    }
}
