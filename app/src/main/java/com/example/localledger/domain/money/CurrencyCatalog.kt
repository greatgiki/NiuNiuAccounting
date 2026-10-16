package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException

data class CurrencySpec(
    val code: String,
    val symbol: String,
    val minorDigits: Int,
    val label: String,
)

object CurrencyCatalog {
    val supported: List<CurrencySpec> = listOf(
        CurrencySpec("CNY", "¥", 2, "人民币"),
        CurrencySpec("USD", "$", 2, "美元"),
        CurrencySpec("EUR", "€", 2, "欧元"),
        CurrencySpec("GBP", "£", 2, "英镑"),
        CurrencySpec("JPY", "円", 0, "日元"),
        CurrencySpec("HKD", "HK$", 2, "港币"),
        CurrencySpec("AUD", "A$", 2, "澳元"),
        CurrencySpec("CAD", "C$", 2, "加元"),
        CurrencySpec("SGD", "S$", 2, "新加坡元"),
        CurrencySpec("CHF", "Fr", 2, "瑞士法郎"),
        CurrencySpec("KRW", "₩", 0, "韩元"),
        CurrencySpec("TWD", "NT$", 2, "新台币"),
    )

    private val byCode = supported.associateBy { it.code }

    fun contains(code: String): Boolean = code in byCode

    fun of(code: String): CurrencySpec =
        byCode[code] ?: throw LedgerException.UnsupportedCurrency(code)

    fun symbol(code: String): String = of(code).symbol
}
