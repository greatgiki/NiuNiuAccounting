package com.example.localledger.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.model.TransactionType

data class AccountCurrencyRow(
    val id: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
)

data class AccountBalanceRow(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
)

data class TransactionDetailRow(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "to_account_name") val toAccountName: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_icon_key") val categoryIconKey: String?,
    @ColumnInfo(name = "account_currency_code") val accountCurrencyCode: String,
    @ColumnInfo(name = "to_account_currency_code") val toAccountCurrencyCode: String?,
)

data class BudgetRow(
    @Embedded val budget: com.example.localledger.data.local.entity.BudgetEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
)

data class RecurringRuleRow(
    @Embedded val rule: com.example.localledger.data.local.entity.RecurringRuleEntity,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "to_account_name") val toAccountName: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "account_currency_code") val accountCurrencyCode: String,
)

data class IncomeExpenseSummaryRow(
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
)

data class CategoryTotalRow(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

data class ExternalKeyRow(
    @ColumnInfo(name = "external_source") val externalSource: String,
    @ColumnInfo(name = "external_id") val externalId: String,
)

data class FuzzyTransactionRow(
    val id: String,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val origin: String,
    @ColumnInfo(name = "external_source") val externalSource: String?,
    @ColumnInfo(name = "external_id") val externalId: String?,
)

data class DayAmountRow(
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val type: TransactionType,
    @ColumnInfo(name = "base_amount_minor") val baseAmountMinor: Long,
)

data class TrendSourceRow(
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

data class LargestExpenseRow(
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val note: String?,
)

data class CategoryFrequencyRow(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "expense_count") val expenseCount: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

