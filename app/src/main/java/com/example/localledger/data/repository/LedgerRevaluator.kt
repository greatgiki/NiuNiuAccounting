package com.example.localledger.data.repository

import com.example.localledger.data.local.database.WalletDatabase
import com.example.localledger.data.mapper.toDomain
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.money.CurrencyConverter
import com.example.localledger.domain.money.HistoricalRatePolicy
import com.example.localledger.domain.money.RevalueMode

/**
 * 重算流水的本位币金额和汇率快照。不改账户原币金额，也不改转账的转入金额。
 * 调用方必须放在同一个数据库事务里：任一行缺少所需汇率时抛出异常，整批回滚。
 */
object LedgerRevaluator {
    suspend fun apply(
        database: WalletDatabase,
        ledgerId: String,
        baseCurrency: String,
        mode: RevalueMode,
        now: Long,
    ): Int {
        val base = CurrencyCatalog.of(baseCurrency).code
        val currencies = database.accountDao().currencies(ledgerId)
            .associate { it.id to CurrencyCatalog.of(it.currencyCode).code }
        val history = database.exchangeRateDao().history(base).map { it.toDomain() }
        var changed = 0
        for (row in database.transactionDao().listByLedger(ledgerId)) {
            val accountCurrency = currencies[row.accountId] ?: throw LedgerException.AccountNotFound()
            val snapshot = if (accountCurrency == base) {
                null
            } else {
                HistoricalRatePolicy.select(history, row.transactionAt, mode)
                    ?: throw LedgerException.ExchangeRateUnavailable()
            }
            val baseAmount = CurrencyConverter.toBaseMinor(row.amountMinor, accountCurrency, base, snapshot)
            if (baseAmount <= 0L) throw LedgerException.InvalidAmount()
            val rate = CurrencyConverter.formatRate(
                CurrencyConverter.basePerAccountUnit(accountCurrency, base, snapshot),
            )
            if (baseAmount == row.baseAmountMinor && rate == row.fxRate) continue
            database.transactionDao().updateValuation(row.id, baseAmount, rate, now)
            changed += 1
        }
        return changed
    }
}
