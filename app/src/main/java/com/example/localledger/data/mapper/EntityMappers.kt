package com.example.localledger.data.mapper

import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.data.local.model.AccountBalanceRow
import com.example.localledger.data.local.model.CategoryTotalRow
import com.example.localledger.data.local.model.IncomeExpenseSummaryRow
import com.example.localledger.data.local.model.TransactionDetailRow
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.model.Transaction
import com.example.localledger.domain.model.TransactionDetail

fun LedgerEntity.toDomain() = Ledger(id, name, currencyCode, createdAt, isArchived)
fun Ledger.toEntity() = LedgerEntity(id, name, currencyCode, createdAt, isArchived)

fun AccountEntity.toDomain() = Account(
    id, ledgerId, name, type, initialBalanceMinor, sortOrder, isArchived, createdAt, updatedAt,
)

fun Account.toEntity() = AccountEntity(
    id, ledgerId, name, type, initialBalanceMinor, sortOrder, isArchived, createdAt, updatedAt,
)

fun CategoryEntity.toDomain() = Category(
    id, ledgerId, name, type, iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

fun Category.toEntity() = CategoryEntity(
    id, ledgerId, name, type, iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

fun TransactionEntity.toDomain() = Transaction(
    id, ledgerId, type, amountMinor, accountId, toAccountId, categoryId,
    transactionAt, note, createdAt, updatedAt,
)

fun AccountBalanceRow.toDomain() = AccountBalance(account.toDomain(), balanceMinor)
fun IncomeExpenseSummaryRow.toDomain() = IncomeExpenseSummary(incomeMinor, expenseMinor)
fun CategoryTotalRow.toDomain() = CategoryTotal(categoryId, categoryName, iconKey, amountMinor)

fun TransactionDetailRow.toDomain() = TransactionDetail(
    transaction = transaction.toDomain(),
    accountName = accountName,
    toAccountName = toAccountName,
    categoryName = categoryName,
    categoryIconKey = categoryIconKey,
)

