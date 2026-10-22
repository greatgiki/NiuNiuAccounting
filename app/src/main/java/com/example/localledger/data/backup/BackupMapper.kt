package com.example.localledger.data.backup

import com.example.localledger.data.local.entity.AccountEntity
import com.example.localledger.data.local.entity.BudgetEntity
import com.example.localledger.data.local.entity.CategoryEntity
import com.example.localledger.data.local.entity.ExchangeRateHistoryEntity
import com.example.localledger.data.local.entity.ExchangeRateSnapshotEntity
import com.example.localledger.data.local.entity.LedgerEntity
import com.example.localledger.data.local.entity.MerchantCategoryRuleEntity
import com.example.localledger.data.local.entity.PendingDraftEntity
import com.example.localledger.data.local.entity.RecurringOccurrenceEntity
import com.example.localledger.data.local.entity.RecurringRuleEntity
import com.example.localledger.data.local.entity.TransactionEntity
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.TransactionType

internal fun LedgerEntity.toRow() = LedgerRow(id, name, currencyCode, createdAt, isArchived)

internal fun AccountEntity.toRow() = AccountRow(
    id, ledgerId, name, type.name, initialBalanceMinor, sortOrder, isArchived, createdAt, updatedAt, currencyCode,
)

internal fun CategoryEntity.toRow() = CategoryRow(
    id, ledgerId, name, type.name, iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

internal fun TransactionEntity.toRow() = TransactionRow(
    id, ledgerId, type.name, amountMinor, accountId, toAccountId, categoryId, transactionAt, note,
    createdAt, updatedAt, toAmountMinor, baseAmountMinor, fxRate, externalSource, externalId, origin, counterparty,
)

internal fun BudgetEntity.toRow() = BudgetRow(
    id, ledgerId, yearMonth, categoryId, amountMinor, createdAt, updatedAt,
)

internal fun RecurringRuleEntity.toRow() = RecurringRuleRow(
    id, ledgerId, type.name, amountMinor, toAmountMinor, accountId, toAccountId, categoryId, note,
    period.name, startDate, endDate, nextRunDate, paused, createdAt, updatedAt,
)

internal fun RecurringOccurrenceEntity.toRow() = RecurringOccurrenceRow(ruleId, occurrenceDate, transactionId)

internal fun ExchangeRateSnapshotEntity.toRow() = ExchangeRateSnapshotRow(baseCode, ratesJson, fetchedAt, source)

internal fun ExchangeRateHistoryEntity.toRow() = ExchangeRateHistoryRow(baseCode, fetchedAt, ratesJson, source)

internal fun MerchantCategoryRuleEntity.toRow() = MerchantCategoryRuleRow(
    id, ledgerId, merchantKey, merchantLabel, type.name, categoryId, updatedAt,
)

internal fun PendingDraftEntity.toRow() = PendingDraftRow(
    id, ledgerId, source, type.name, amountMinor, counterparty, note, rawText, categoryId, accountId,
    transactionAt, status, transactionId, notificationKey, likelyDuplicate, createdAt, updatedAt,
    needsAmount, sbnKey, groupKey,
)

internal fun LedgerRow.toEntity() = LedgerEntity(id, name, currencyCode, createdAt, isArchived)

internal fun AccountRow.toEntity() = AccountEntity(
    id, ledgerId, name, AccountType.valueOf(type), initialBalanceMinor, sortOrder, isArchived,
    createdAt, updatedAt, currencyCode,
)

internal fun CategoryRow.toEntity() = CategoryEntity(
    id, ledgerId, name, CategoryType.valueOf(type), iconKey, sortOrder, isArchived, createdAt, updatedAt,
)

internal fun TransactionRow.toEntity() = TransactionEntity(
    id, ledgerId, TransactionType.valueOf(type), amountMinor, accountId, toAccountId, categoryId,
    transactionAt, note, createdAt, updatedAt, toAmountMinor, baseAmountMinor, fxRate,
    externalSource, externalId, origin, counterparty,
)

internal fun BudgetRow.toEntity() = BudgetEntity(
    id, ledgerId, yearMonth, categoryId, amountMinor, createdAt, updatedAt,
)

internal fun RecurringRuleRow.toEntity() = RecurringRuleEntity(
    id, ledgerId, TransactionType.valueOf(type), amountMinor, toAmountMinor, accountId, toAccountId,
    categoryId, note, RecurrencePeriod.valueOf(period), startDate, endDate, nextRunDate, paused,
    createdAt, updatedAt,
)

internal fun RecurringOccurrenceRow.toEntity() = RecurringOccurrenceEntity(ruleId, occurrenceDate, transactionId)

internal fun ExchangeRateSnapshotRow.toEntity() = ExchangeRateSnapshotEntity(baseCode, ratesJson, fetchedAt, source)

internal fun ExchangeRateHistoryRow.toEntity() = ExchangeRateHistoryEntity(baseCode, fetchedAt, ratesJson, source)

internal fun MerchantCategoryRuleRow.toEntity() = MerchantCategoryRuleEntity(
    id, ledgerId, merchantKey, merchantLabel, TransactionType.valueOf(type), categoryId, updatedAt,
)

internal fun PendingDraftRow.toEntity() = PendingDraftEntity(
    id, ledgerId, source, TransactionType.valueOf(type), amountMinor, counterparty, note, rawText,
    categoryId, accountId, transactionAt, status, transactionId, notificationKey, likelyDuplicate,
    createdAt, updatedAt, needsAmount, sbnKey, groupKey,
)
