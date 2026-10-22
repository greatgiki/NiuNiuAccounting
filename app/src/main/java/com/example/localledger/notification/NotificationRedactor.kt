package com.example.localledger.notification

object NotificationRedactor {
    private val number = """[0-9０-９]{1,12}(?:[,，][0-9０-９]{3})*(?:[.．][0-9０-９]{1,2})?"""
    private val amount = Regex(
        """(?:[¥￥]\s*$number|$number\s*元|(?:人民币|CNY|RMB)\s*$number)""",
        RegexOption.IGNORE_CASE,
    )
    private val labeledParty = Regex(
        """((?:商户|商家|店铺|店名|收款方|付款方|对方|交易对象)(?:名称|全称)?)(\s*[:：]\s*)(\S{1,40})""",
    )
    private val payTo = Regex("""向([^\n]{1,40}?)付款""")
    private val payPerson = Regex("""付款给(\S{1,30})""")
    private val spentAt = Regex("""在([^\n]{1,30}?)消费""")

    fun redact(text: String): String {
        val merchants = labeledParty.replace(text) { match ->
            match.groupValues[1] + match.groupValues[2] + "**"
        }
        val directed = payTo.replace(merchants) { "向**付款" }
        val person = payPerson.replace(directed) { "付款给**" }
        val place = spentAt.replace(person) { "在**消费" }
        return amount.replace(place, "**")
    }
}
