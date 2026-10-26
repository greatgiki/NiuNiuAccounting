package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillAmounts
import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.TransactionType
import java.time.ZoneId

/**
 * Mainland bank texts. A message is kept only when it names a bank and an
 * amount next to 支出 / 收入 / 转账. Verification codes are dropped.
 */
object BankSmsParser {
    private val banks = listOf(
        "工商银行",
        "农业银行",
        "中国银行",
        "建设银行",
        "交通银行",
        "邮储银行",
        "邮政储蓄",
        "招商银行",
        "浦发银行",
        "中信银行",
        "光大银行",
        "华夏银行",
        "民生银行",
        "兴业银行",
        "平安银行",
        "广发银行",
        "北京银行",
        "上海银行",
        "宁波银行",
        "江苏银行",
        "南京银行",
        "浙商银行",
        "渤海银行",
        "恒丰银行",
        "徽商银行",
    )

    private val anchors = listOf("支出", "消费", "支付", "收入", "入账", "转入", "转出", "退款", "转账")
    private val money = Regex("""(?:人民币|RMB|CNY)?\s*([0-9]{1,7}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元""")
    private val tail = Regex("""尾号\s*([0-9]{4})""")
    private val accountTail = Regex("""(?:账户|卡号|借记卡|储蓄卡)\s*([0-9]{4})""")
    private val merchant = Regex("""商户[:：]\s*([^\s，,。]{1,30})""")
    private val payee = Regex("""收款人[:：]\s*([^\s，,。]{1,30})""")
    private val payTo = Regex("""向([^\s，,。]{1,30}?)支付""")

    fun parse(body: String, receivedAt: Long, zone: ZoneId): ParsedCapture? {
        val text = body.trim()
        if (text.length !in 12..500) return null
        val code = listOf("验证码", "校验码", "动态密码").any { phrase -> phrase in text }
        val movement = anchors.any { phrase -> phrase in text }
        if (code && !movement) return null
        val bank = banks.firstOrNull { name -> name in text } ?: return null
        if (!movement) return null
        val amount = amount(text) ?: return null
        val tailNumber = tail.find(text)?.groupValues?.getOrNull(1)
            ?: accountTail.find(text)?.groupValues?.getOrNull(1)
        val who = merchant.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .ifBlank { payee.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty() }
            .ifBlank { payTo.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty() }
        val account = buildString {
            append(bank)
            if (!tailNumber.isNullOrBlank()) append("尾号").append(tailNumber)
        }
        return ParsedCapture(
            source = BillSource.BANK,
            page = CapturePage.SUCCESS,
            type = type(text),
            amountMinor = amount,
            merchant = who,
            accountLabel = account,
            occurredAt = PaymentTimes.parse(text, receivedAt, zone) ?: receivedAt,
            ruleId = "bank-sms",
        )
    }

    private fun amount(text: String): Long? {
        val beforeBalance = text.substringBefore("余额").substringBefore("可用余额")
        val index = anchors.map { anchor -> beforeBalance.indexOf(anchor) }.filter { it >= 0 }.minOrNull() ?: return null
        val raw = money.find(beforeBalance.substring(index))?.groupValues?.getOrNull(1) ?: return null
        return BillAmounts.parseYuanToMinor(raw)
    }

    private fun type(text: String): TransactionType {
        if (listOf("转入本人", "账户互转", "本行账户").any { phrase -> phrase in text }) return TransactionType.TRANSFER
        if (listOf("退款", "收入", "入账", "转入").any { phrase -> phrase in text }) return TransactionType.INCOME
        return TransactionType.EXPENSE
    }
}
