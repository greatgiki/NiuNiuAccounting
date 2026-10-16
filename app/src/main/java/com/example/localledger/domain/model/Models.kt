package com.example.localledger.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

enum class CategoryType { EXPENSE, INCOME }

enum class AccountType { CASH, BANK_CARD, E_WALLET, CREDIT_CARD, OTHER }

data class Ledger(
    val id: String,
    val name: String,
    val currencyCode: String = "CNY",
    val createdAt: Long,
    val isArchived: Boolean = false,
)

data class Account(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: AccountType,
    val currencyCode: String = "CNY",
    val initialBalanceMinor: Long = 0,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Category(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Transaction(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val transactionAt: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val toAmountMinor: Long? = null,
    val baseAmountMinor: Long = amountMinor,
    val fxRate: String = "1",
)

data class AccountBalance(
    val account: Account,
    val balanceMinor: Long,
)

data class TransactionDetail(
    val transaction: Transaction,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val accountCurrencyCode: String = "CNY",
    val toAccountCurrencyCode: String? = null,
)

data class DateTimeRange(
    val startInclusive: Long,
    val endExclusive: Long,
) {
    init {
        require(startInclusive < endExclusive) { "Time range must not be empty" }
    }
}

sealed interface StatisticsPeriod {
    data class Day(val date: LocalDate) : StatisticsPeriod
    data class Month(val yearMonth: YearMonth) : StatisticsPeriod
    data class Year(val year: Int) : StatisticsPeriod
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : StatisticsPeriod
}

data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val range: DateTimeRange? = null,
    val keyword: String = "",
    val limit: Int? = null,
)

data class SaveTransactionCommand(
    val transactionId: String? = null,
    val ledgerId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val transactionAt: Long,
    val note: String? = null,
    /** 转入金额，币种为转入账户。为空时按汇率或同币种原金额计算。 */
    val toAmountMinor: Long? = null,
)

data class IncomeExpenseSummary(
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    val balanceMinor: Long
        get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class CategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val iconKey: String,
    val amountMinor: Long,
)

data class DailyTotal(
    val date: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class MonthlyTotal(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

enum class RecurrencePeriod { DAILY, WEEKLY, MONTHLY, YEARLY }

data class Budget(
    val id: String,
    val ledgerId: String,
    val yearMonth: YearMonth,
    val categoryId: String?,
    val amountMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val categoryName: String? = null,
)

data class RecurringRule(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val toAmountMinor: Long?,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val note: String?,
    val period: RecurrencePeriod,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextRunDate: LocalDate,
    val paused: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val accountCurrencyCode: String = "CNY",
    val accountName: String = "",
    val toAccountName: String? = null,
    val categoryName: String? = null,
)

data class ExchangeRateSnapshot(
    val baseCode: String,
    /** 1 单位本位币可兑换的报价币数量，与 Frankfurter `from=base` 的 rates 一致。 */
    val quotesPerUnit: Map<String, BigDecimal>,
    val fetchedAt: Long,
    val source: String,
)

