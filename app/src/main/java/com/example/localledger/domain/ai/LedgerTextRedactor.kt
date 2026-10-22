package com.example.localledger.domain.ai

object LedgerTextRedactor {
    private val spacedCard = Regex("""(?<!\d)(?:\d[ -]?){12,19}(?!\d)""")
    private val longDigits = Regex("""\d{12,}""")
    private val tail = Regex("""尾号\s*\d{2,8}""")

    fun redact(text: String): String {
        val cards = spacedCard.replace(text, "****")
        val digits = longDigits.replace(cards, "****")
        return tail.replace(digits, "尾号****")
    }

    fun looksLikeAccountSecret(text: String): Boolean = text.count { it.isDigit() } >= 12
}
