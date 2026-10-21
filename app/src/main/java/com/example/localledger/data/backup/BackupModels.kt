package com.example.localledger.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupDocument(
    val formatVersion: Int = BackupCodec.FORMAT_VERSION,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val tables: BackupTables,
)

@Serializable
data class BackupTables(
    val ledgers: List<LedgerRow> = emptyList(),
    val accounts: List<AccountRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val budgets: List<BudgetRow> = emptyList(),
    val recurringRules: List<RecurringRuleRow> = emptyList(),
    val recurringOccurrences: List<RecurringOccurrenceRow> = emptyList(),
    val exchangeRateSnapshots: List<ExchangeRateSnapshotRow> = emptyList(),
    val exchangeRateHistory: List<ExchangeRateHistoryRow> = emptyList(),
    val pendingDrafts: List<PendingDraftRow> = emptyList(),
    val merchantCategoryRules: List<MerchantCategoryRuleRow> = emptyList(),
)

@Serializable
data class LedgerRow(
    val id: String,
    val name: String,
    val currencyCode: String,
    val createdAt: Long,
    val isArchived: Boolean = false,
)

@Serializable
data class AccountRow(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: String,
    val initialBalanceMinor: Long = 0,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val currencyCode: String = "CNY",
)

@Serializable
data class CategoryRow(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: String,
    val iconKey: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TransactionRow(
    val id: String,
    val ledgerId: String,
    val type: String,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val transactionAt: Long,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val toAmountMinor: Long? = null,
    val baseAmountMinor: Long = 0,
    val fxRate: String = "1",
    val externalSource: String? = null,
    val externalId: String? = null,
    val origin: String = "MANUAL",
    val counterparty: String? = null,
)

@Serializable
data class BudgetRow(
    val id: String,
    val ledgerId: String,
    val yearMonth: String,
    val categoryId: String? = null,
    val amountMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RecurringRuleRow(
    val id: String,
    val ledgerId: String,
    val type: String,
    val amountMinor: Long,
    val toAmountMinor: Long? = null,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val period: String,
    val startDate: String,
    val endDate: String? = null,
    val nextRunDate: String,
    val paused: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RecurringOccurrenceRow(
    val ruleId: String,
    val occurrenceDate: String,
    val transactionId: String,
)

@Serializable
data class ExchangeRateSnapshotRow(
    val baseCode: String,
    val ratesJson: String,
    val fetchedAt: Long,
    val source: String,
)

@Serializable
data class ExchangeRateHistoryRow(
    val baseCode: String,
    val fetchedAt: Long,
    val ratesJson: String,
    val source: String,
)

@Serializable
data class PendingDraftRow(
    val id: String,
    val ledgerId: String,
    val source: String,
    val type: String,
    val amountMinor: Long,
    val counterparty: String? = null,
    val note: String? = null,
    val rawText: String,
    val categoryId: String? = null,
    val accountId: String? = null,
    val transactionAt: Long,
    val status: String,
    val transactionId: String? = null,
    val notificationKey: String? = null,
    val likelyDuplicate: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val needsAmount: Boolean = false,
    val sbnKey: String? = null,
    val groupKey: String? = null,
)

@Serializable
data class MerchantCategoryRuleRow(
    val id: String,
    val ledgerId: String,
    val merchantKey: String,
    val merchantLabel: String,
    val type: String,
    val categoryId: String,
    val updatedAt: Long,
)

data class BackupSummary(
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val ledgerCount: Int,
    val accountCount: Int,
    val transactionCount: Int,
)

class BackupFailure(
    message: String,
    val kind: Kind = Kind.Generic,
) : Exception(message) {
    enum class Kind { Generic, NeedsPassword, WrongPassword, Corrupt }
}
