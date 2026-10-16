package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 汇率表的语义与 Frankfurter 一致：`quotesPerUnit[C]` 表示 1 单位本位币可兑换的 C 数量。
 * 例如本位币 CNY、USD = 0.14，即 1 CNY = 0.14 USD。
 */
object CurrencyConverter {
    private const val DIVISION_SCALE = 16

    fun toBaseMinor(
        amountMinor: Long,
        accountCurrency: String,
        ledgerCurrency: String,
        snapshot: ExchangeRateSnapshot?,
    ): Long {
        if (accountCurrency == ledgerCurrency) return amountMinor
        val table = snapshot ?: throw LedgerException.ExchangeRateUnavailable()
        if (table.baseCode != ledgerCurrency) throw LedgerException.ExchangeRateUnavailable()
        val quote = table.quotesPerUnit[accountCurrency] ?: throw LedgerException.ExchangeRateUnavailable()
        return convertUsingQuotePerBase(
            amountMinor = amountMinor,
            fromCode = accountCurrency,
            toCode = ledgerCurrency,
            unitsOfFromPerUnitOfTo = quote,
        )
    }

    fun betweenAccounts(
        amountMinor: Long,
        fromCurrency: String,
        toCurrency: String,
        ledgerCurrency: String,
        snapshot: ExchangeRateSnapshot?,
    ): Long {
        if (fromCurrency == toCurrency) return amountMinor
        val table = snapshot ?: throw LedgerException.ExchangeRateUnavailable()
        if (table.baseCode != ledgerCurrency) throw LedgerException.ExchangeRateUnavailable()
        val fromQuote = quoteOf(fromCurrency, ledgerCurrency, table)
        val toQuote = quoteOf(toCurrency, ledgerCurrency, table)
        val toPerFrom = toQuote.divide(fromQuote, DIVISION_SCALE, RoundingMode.HALF_UP)
        return multiplyConvert(amountMinor, fromCurrency, toCurrency, toPerFrom)
    }

    /** 1 单位来源币兑多少单位目标币，用于写入流水上的 fx_rate。 */
    fun basePerAccountUnit(
        accountCurrency: String,
        ledgerCurrency: String,
        snapshot: ExchangeRateSnapshot?,
    ): BigDecimal {
        if (accountCurrency == ledgerCurrency) return BigDecimal.ONE
        val table = snapshot ?: throw LedgerException.ExchangeRateUnavailable()
        val quote = table.quotesPerUnit[accountCurrency] ?: throw LedgerException.ExchangeRateUnavailable()
        return BigDecimal.ONE.divide(quote, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    fun formatRate(rate: BigDecimal): String = rate.stripTrailingZeros().toPlainString()

    private fun quoteOf(
        currency: String,
        ledgerCurrency: String,
        table: ExchangeRateSnapshot,
    ): BigDecimal {
        if (currency == ledgerCurrency) return BigDecimal.ONE
        return table.quotesPerUnit[currency] ?: throw LedgerException.ExchangeRateUnavailable()
    }

    private fun convertUsingQuotePerBase(
        amountMinor: Long,
        fromCode: String,
        toCode: String,
        unitsOfFromPerUnitOfTo: BigDecimal,
    ): Long {
        if (unitsOfFromPerUnitOfTo.signum() <= 0) throw LedgerException.ExchangeRateUnavailable()
        val from = CurrencyCatalog.of(fromCode)
        val to = CurrencyCatalog.of(toCode)
        val major = BigDecimal.valueOf(amountMinor).movePointLeft(from.minorDigits)
        val toMajor = major.divide(unitsOfFromPerUnitOfTo, DIVISION_SCALE, RoundingMode.HALF_UP)
        return toMinor(toMajor, to.minorDigits)
    }

    private fun multiplyConvert(
        amountMinor: Long,
        fromCode: String,
        toCode: String,
        toPerFrom: BigDecimal,
    ): Long {
        if (toPerFrom.signum() <= 0) throw LedgerException.ExchangeRateUnavailable()
        val from = CurrencyCatalog.of(fromCode)
        val to = CurrencyCatalog.of(toCode)
        val major = BigDecimal.valueOf(amountMinor).movePointLeft(from.minorDigits)
        return toMinor(major.multiply(toPerFrom), to.minorDigits)
    }

    private fun toMinor(major: BigDecimal, minorDigits: Int): Long =
        try {
            major.movePointRight(minorDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }
}
