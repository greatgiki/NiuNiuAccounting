package com.example.localledger.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.model.TransactionType

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

data class TrendSourceRow(
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

