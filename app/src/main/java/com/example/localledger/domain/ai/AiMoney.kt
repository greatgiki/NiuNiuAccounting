package com.example.localledger.domain.ai

import com.example.localledger.domain.money.CurrencyCatalog
import java.math.BigInteger

object AiMoney {
    fun major(amountMinor: Long, currencyCode: String): String {
        val digits = runCatching { CurrencyCatalog.of(currencyCode).minorDigits }.getOrDefault(2)
        val negative = amountMinor < 0
        val absolute = BigInteger.valueOf(amountMinor).abs()
        val factor = BigInteger.TEN.pow(digits)
        val whole = if (digits == 0) absolute else absolute.divide(factor)
        val fraction = if (digits == 0) {
            ""
        } else {
            "." + absolute.remainder(factor).toString().padStart(digits, '0')
        }
        return (if (negative) "-" else "") + whole.toString() + fraction
    }

    fun labeled(amountMinor: Long, currencyCode: String): String {
        val symbol = runCatching { CurrencyCatalog.of(currencyCode).symbol }.getOrDefault("")
        return symbol + major(amountMinor, currencyCode)
    }
}
