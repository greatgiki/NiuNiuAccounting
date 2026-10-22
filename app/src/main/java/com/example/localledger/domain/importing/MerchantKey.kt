package com.example.localledger.domain.importing

import java.util.Locale

/**
 * Stable key for a merchant or counterparty. Spacing and Latin case do not
 * create a second mapping; the display label keeps the user's latest wording.
 */
object MerchantKey {
    fun normalize(raw: String): String {
        val collapsed = collapse(raw)
        if (collapsed.isEmpty()) return ""
        return collapsed.lowercase(Locale.ROOT)
    }

    fun display(raw: String): String = collapse(raw)

    private fun collapse(raw: String): String {
        return raw
            .replace('\u3000', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
