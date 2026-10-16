package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 把「1 人民币可兑换多少外币」交叉成账本本位币的报价。
 * 结果与 [com.example.localledger.domain.model.ExchangeRateSnapshot.quotesPerUnit] 一致：
 * 1 单位本位币可兑换的报价币数量。
 */
object CnyCrossRates {
    private const val SCALE = 16

    fun quotesPerUnit(foreignPerOneCny: Map<String, BigDecimal>, baseCode: String): Map<String, BigDecimal> {
        val base = CurrencyCatalog.of(baseCode).code
        val basePerCny = if (base == "CNY") {
            BigDecimal.ONE
        } else {
            foreignPerOneCny[base] ?: throw LedgerException.ExchangeRateUnavailable()
        }
        if (basePerCny.signum() <= 0) throw LedgerException.ExchangeRateUnavailable()
        val quotes = LinkedHashMap<String, BigDecimal>()
        if (base != "CNY") {
            quotes["CNY"] = BigDecimal.ONE.divide(basePerCny, SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
        }
        foreignPerOneCny.forEach { (code, perCny) ->
            if (code == base || perCny.signum() <= 0 || !CurrencyCatalog.contains(code)) return@forEach
            val quote = if (base == "CNY") {
                perCny
            } else {
                perCny.divide(basePerCny, SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
            }
            if (quote.signum() > 0) quotes[code] = quote
        }
        if (quotes.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        return quotes
    }
}
