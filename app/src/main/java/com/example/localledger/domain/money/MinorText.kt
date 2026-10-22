package com.example.localledger.domain.money

import java.math.BigInteger

/** Ledger amounts for reports and the on-screen money text. Same digits, no grouping separators. */
object MinorText {
    fun format(amountMinor: Long, currencyCode: String = "CNY"): String {
        val spec = CurrencyCatalog.of(currencyCode)
        val negative = amountMinor < 0
        val abs = BigInteger.valueOf(amountMinor).abs()
        val factor = BigInteger.TEN.pow(spec.minorDigits)
        val major = if (spec.minorDigits == 0) abs else abs.divide(factor)
        val fraction = if (spec.minorDigits == 0) 0 else abs.remainder(factor).toInt()
        val sign = if (negative) "-" else ""
        val digits = if (spec.minorDigits == 0) {
            "$major"
        } else {
            "$major.${fraction.toString().padStart(spec.minorDigits, '0')}"
        }
        return "$sign${spec.symbol}$digits"
    }
}
