package com.example.localledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.TransactionType

@Entity(tableName = "ledgers")
data class LedgerEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code", defaultValue = "'CNY'") val currencyCode: String = "CNY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["ledger_id"]),
        Index(value = ["ledger_id", "is_archived", "sort_order"]),
    ],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val name: String,
    val type: AccountType,
    @ColumnInfo(name = "initial_balance_minor", defaultValue = "0") val initialBalanceMinor: Long = 0,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "currency_code", defaultValue = "'CNY'") val currencyCode: String = "CNY",
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["ledger_id"]),
        Index(value = ["ledger_id", "type", "is_archived", "sort_order"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val name: String,
    val type: CategoryType,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["to_account_id"]),
        Index(value = ["category_id"]),
        Index(
            value = ["ledger_id", "transaction_at"],
            orders = [Index.Order.ASC, Index.Order.DESC],
        ),
        Index(
            value = ["ledger_id", "type", "transaction_at"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC],
        ),
        Index(
            value = ["ledger_id", "category_id", "transaction_at"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC],
        ),
        Index(
            value = ["ledger_id", "account_id", "transaction_at"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC],
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "transaction_at") val transactionAt: Long,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "to_amount_minor") val toAmountMinor: Long? = null,
    @ColumnInfo(name = "base_amount_minor", defaultValue = "0") val baseAmountMinor: Long = 0,
    @ColumnInfo(name = "fx_rate", defaultValue = "'1'") val fxRate: String = "1",
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "year_month"]),
        Index(value = ["category_id"]),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "year_month") val yearMonth: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "paused"]),
        Index(value = ["account_id"]),
        Index(value = ["to_account_id"]),
        Index(value = ["category_id"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "to_amount_minor") val toAmountMinor: Long?,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val note: String?,
    val period: RecurrencePeriod,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String?,
    @ColumnInfo(name = "next_run_date") val nextRunDate: String,
    @ColumnInfo(defaultValue = "0") val paused: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "recurring_occurrences",
    primaryKeys = ["rule_id", "occurrence_date"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
)
data class RecurringOccurrenceEntity(
    @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(name = "occurrence_date") val occurrenceDate: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
)

@Entity(tableName = "exchange_rate_snapshots")
data class ExchangeRateSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "base_code") val baseCode: String,
    @ColumnInfo(name = "rates_json") val ratesJson: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    val source: String,
)
