package com.example.localledger.data.mapper

import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.data.local.model.AccountBalanceRow
import com.example.localledger.data.local.model.BudgetRow
import com.example.localledger.data.local.model.CategoryTotalRow
import com.example.localledger.data.local.model.IncomeExpenseSummaryRow
import com.example.localledger.data.local.model.RecurringRuleRow
import com.example.localledger.data.local.model.TransactionDetailRow
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.Transaction
import com.example.localledger.domain.model.TransactionDetail
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun LedgerEntity.toDomain() = Ledger(id, name, currencyCode, createdAt, isArchived)
fun Ledger.toEntity() = LedgerEntity(id, name, currencyCode, createdAt, isArchived)

fun AccountEntity.toDomain() = Account(
    id, ledgerId, name, type, currencyCode, initialBalanceMinor, sortOrder, isArchived, createdAt, updatedAt,
)

fun Account.toEntity() = AccountEntity(
    id, ledgerId, name, type, initialBalanceMinor, sortOrder, isArchived, createdAt, updatedAt, currencyCode,
)

fun CategoryEntity.toDomain() = Category(
    id, ledgerId, name, type, iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

fun Category.toEntity() = CategoryEntity(
    id, ledgerId, name, type, iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

fun TransactionEntity.toDomain() = Transaction(
    id, ledgerId, type, amountMinor, accountId, toAccountId, categoryId,
    transactionAt, note, createdAt, updatedAt, toAmountMinor, baseAmountMinor, fxRate,
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
    accountCurrencyCode = accountCurrencyCode,
    toAccountCurrencyCode = toAccountCurrencyCode,
)

fun BudgetRow.toDomain() = Budget(
    id = budget.id,
    ledgerId = budget.ledgerId,
    yearMonth = YearMonth.parse(budget.yearMonth),
    categoryId = budget.categoryId,
    amountMinor = budget.amountMinor,
    createdAt = budget.createdAt,
    updatedAt = budget.updatedAt,
    categoryName = categoryName,
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    ledgerId = ledgerId,
    yearMonth = yearMonth.toString(),
    categoryId = categoryId,
    amountMinor = amountMinor,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecurringRuleRow.toDomain(): RecurringRule {
    val entity = rule
    return RecurringRule(
        id = entity.id,
        ledgerId = entity.ledgerId,
        type = entity.type,
        amountMinor = entity.amountMinor,
        toAmountMinor = entity.toAmountMinor,
        accountId = entity.accountId,
        toAccountId = entity.toAccountId,
        categoryId = entity.categoryId,
        note = entity.note,
        period = entity.period,
        startDate = LocalDate.parse(entity.startDate),
        endDate = entity.endDate?.let(LocalDate::parse),
        nextRunDate = LocalDate.parse(entity.nextRunDate),
        paused = entity.paused,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        accountCurrencyCode = accountCurrencyCode,
        accountName = accountName,
        toAccountName = toAccountName,
        categoryName = categoryName,
    )
}

fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    ledgerId = ledgerId,
    type = type,
    amountMinor = amountMinor,
    toAmountMinor = toAmountMinor,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    period = period,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    nextRunDate = nextRunDate.toString(),
    paused = paused,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private val rateJson = Json { ignoreUnknownKeys = true }

fun ExchangeRateSnapshotEntity.toDomain(): ExchangeRateSnapshot {
    val raw = rateJson.decodeFromString<Map<String, String>>(ratesJson)
    return ExchangeRateSnapshot(
        baseCode = baseCode,
        quotesPerUnit = raw.mapValues { BigDecimal(it.value) },
        fetchedAt = fetchedAt,
        source = source,
    )
}

fun ExchangeRateSnapshot.toEntity() = ExchangeRateSnapshotEntity(
    baseCode = baseCode,
    ratesJson = rateJson.encodeToString(
        quotesPerUnit.mapValues { it.value.stripTrailingZeros().toPlainString() },
    ),
    fetchedAt = fetchedAt,
    source = source,
)

