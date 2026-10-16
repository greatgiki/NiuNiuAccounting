package com.example.localledger.domain.importing

import java.math.BigDecimal
import java.math.RoundingMode

object BillAmounts {
    fun parseYuanToMinor(raw: String): Long? {
        var text = raw.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(",", "")
            .replace("，", "")
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u3000", "")
        if (text.isEmpty() || text == "/" || text == "／" || text == "-" || text == "--") return null
        text = text.removePrefix("+").removePrefix("-")
        if (text.isEmpty()) return null
        val minor = try {
            BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            return null
        }
        return minor.takeIf { it > 0L }
    }
}
