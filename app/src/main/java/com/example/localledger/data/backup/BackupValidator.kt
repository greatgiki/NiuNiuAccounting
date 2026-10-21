package com.example.localledger.data.backup

import com.example.localledger.data.local.database.WALLET_SCHEMA_VERSION
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.TransactionType

object BackupValidator {
    fun validate(document: BackupDocument, expectedSchema: Int = WALLET_SCHEMA_VERSION): BackupSummary {
        if (document.formatVersion != BackupCodec.FORMAT_VERSION) {
            fail("无法识别的备份格式，请更新牛牛记账后再试")
        }
        if (document.schemaVersion > expectedSchema) {
            fail("这份备份来自更新的数据库（v${document.schemaVersion}）。请先更新牛牛记账，再恢复。")
        }
        if (document.schemaVersion < expectedSchema) {
            fail("这份备份的数据库版本是 v${document.schemaVersion}，当前应用是 v$expectedSchema，无法直接恢复。")
        }
        if (document.createdAtEpochMillis < 0) fail("备份文件已损坏或无法读取")
        val tables = document.tables
        if (tables.ledgers.isEmpty()) fail("备份里没有账本，已取消恢复。")
        val ledgerIds = uniqueIds(tables.ledgers.map { it.id }, "账本")
        tables.ledgers.forEach { ledger ->
            if (ledger.name.isBlank()) fail("备份里有未命名的账本")
            if (ledger.currencyCode.isBlank()) fail("备份里有缺少币种的账本")
        }
        val accountIds = uniqueIds(tables.accounts.map { it.id }, "账户")
        tables.accounts.forEach { account ->
            if (account.ledgerId !in ledgerIds) fail("备份数据不完整：账户引用了不存在的账本")
            if (account.name.isBlank()) fail("备份里有未命名的账户")
            if (!enumOk<AccountType>(account.type)) fail("备份里有无法识别的账户类型")
        }
        val accountsById = tables.accounts.associateBy { it.id }
        val categoryIds = uniqueIds(tables.categories.map { it.id }, "分类")
        tables.categories.forEach { category ->
            if (category.ledgerId !in ledgerIds) fail("备份数据不完整：分类引用了不存在的账本")
            if (category.name.isBlank() || category.iconKey.isBlank()) fail("备份里有不完整的分类")
            if (!enumOk<CategoryType>(category.type)) fail("备份里有无法识别的分类类型")
        }
        val categoriesById = tables.categories.associateBy { it.id }
        uniqueIds(tables.transactions.map { it.id }, "流水")
        val externalKeys = HashSet<String>()
        tables.transactions.forEach { row ->
            if (row.ledgerId !in ledgerIds) fail("备份数据不完整：流水引用了不存在的账本")
            if (!enumOk<TransactionType>(row.type)) fail("备份里有无法识别的流水类型")
            val account = accountsById[row.accountId]
                ?: fail("备份数据不完整：流水引用了不存在的账户")
            if (account.ledgerId != row.ledgerId) fail("备份数据不完整：流水和账户不属于同一个账本")
            if (row.amountMinor <= 0 || row.baseAmountMinor <= 0) fail("备份里有金额无效的流水")
            if (row.fxRate.isBlank()) fail("备份里有缺少汇率的流水")
            when (row.type) {
                TransactionType.TRANSFER.name -> {
                    val destination = accountsById[row.toAccountId]
                        ?: fail("备份数据不完整：转账引用了不存在的账户")
                    if (destination.ledgerId != row.ledgerId || destination.id == account.id) {
                        fail("备份数据不完整：转账账户无效")
                    }
                    if (row.toAmountMinor == null || row.toAmountMinor <= 0) fail("备份里有金额无效的转账")
                    if (row.categoryId != null) fail("备份数据不完整：转账不应带有分类")
                }
                else -> {
                    if (row.toAccountId != null || row.toAmountMinor != null) {
                        fail("备份数据不完整：收入或支出不应带有转入账户")
                    }
                    val category = categoriesById[row.categoryId]
                        ?: fail("备份数据不完整：流水引用了不存在的分类")
                    if (category.ledgerId != row.ledgerId || category.type != row.type) {
                        fail("备份数据不完整：流水分类与账本或类型不一致")
                    }
                }
            }
            if (row.externalSource != null && row.externalId != null) {
                val key = "${row.ledgerId}|${row.externalSource}|${row.externalId}"
                if (!externalKeys.add(key)) fail("备份里有重复的外部账单")
            }
        }
        uniqueIds(tables.budgets.map { it.id }, "预算")
        tables.budgets.forEach { budget ->
            if (budget.ledgerId !in ledgerIds) fail("备份数据不完整：预算引用了不存在的账本")
            if (budget.yearMonth.isBlank()) fail("备份里有缺少月份的预算")
            val categoryId = budget.categoryId ?: return@forEach
            val category = categoriesById[categoryId] ?: fail("备份数据不完整：预算引用了不存在的分类")
            if (category.ledgerId != budget.ledgerId) fail("备份数据不完整：预算和分类不属于同一个账本")
        }
        val ruleIds = uniqueIds(tables.recurringRules.map { it.id }, "周期规则")
        tables.recurringRules.forEach { rule ->
            if (rule.ledgerId !in ledgerIds) fail("备份数据不完整：周期规则引用了不存在的账本")
            if (!enumOk<TransactionType>(rule.type) || !enumOk<RecurrencePeriod>(rule.period)) {
                fail("备份里有无法识别的周期规则")
            }
            val account = accountsById[rule.accountId] ?: fail("备份数据不完整：周期规则引用了不存在的账户")
            if (account.ledgerId != rule.ledgerId) fail("备份数据不完整：周期规则和账户不属于同一个账本")
            if (rule.toAccountId != null) {
                val destination = accountsById[rule.toAccountId]
                    ?: fail("备份数据不完整：周期规则引用了不存在的账户")
                if (destination.ledgerId != rule.ledgerId) {
                    fail("备份数据不完整：周期规则和账户不属于同一个账本")
                }
            }
            if (rule.categoryId != null) {
                val category = categoriesById[rule.categoryId]
                    ?: fail("备份数据不完整：周期规则引用了不存在的分类")
                if (category.ledgerId != rule.ledgerId) {
                    fail("备份数据不完整：周期规则和分类不属于同一个账本")
                }
            }
            if (rule.startDate.isBlank() || rule.nextRunDate.isBlank()) fail("备份里有缺少日期的周期规则")
        }
        val occurrenceKeys = HashSet<String>()
        tables.recurringOccurrences.forEach { occurrence ->
            if (occurrence.ruleId !in ruleIds) fail("备份数据不完整：周期记录引用了不存在的规则")
            if (occurrence.occurrenceDate.isBlank() || occurrence.transactionId.isBlank()) {
                fail("备份里有不完整的周期记录")
            }
            if (!occurrenceKeys.add("${occurrence.ruleId}|${occurrence.occurrenceDate}")) {
                fail("备份里有重复的周期记录")
            }
        }
        uniqueIds(tables.exchangeRateSnapshots.map { it.baseCode }, "汇率")
        tables.exchangeRateSnapshots.forEach { snapshot ->
            if (snapshot.ratesJson.isBlank() || snapshot.source.isBlank()) fail("备份里有不完整的汇率")
        }
        val historyKeys = HashSet<String>()
        tables.exchangeRateHistory.forEach { history ->
            if (history.baseCode.isBlank() || history.ratesJson.isBlank() || history.source.isBlank()) {
                fail("备份里有不完整的汇率")
            }
            if (!historyKeys.add("${history.baseCode}|${history.fetchedAt}")) fail("备份里有重复的汇率记录")
        }
        uniqueIds(tables.pendingDrafts.map { it.id }, "待确认")
        val notificationKeys = HashSet<String>()
        tables.pendingDrafts.forEach { draft ->
            if (draft.ledgerId !in ledgerIds) fail("备份数据不完整：待确认引用了不存在的账本")
            if (!enumOk<TransactionType>(draft.type)) fail("备份里有无法识别的待确认")
            if (draft.accountId != null) {
                val account = accountsById[draft.accountId] ?: fail("备份数据不完整：待确认引用了不存在的账户")
                if (account.ledgerId != draft.ledgerId) fail("备份数据不完整：待确认和账户不属于同一个账本")
            }
            if (draft.categoryId != null) {
                val category = categoriesById[draft.categoryId] ?: fail("备份数据不完整：待确认引用了不存在的分类")
                if (category.ledgerId != draft.ledgerId) fail("备份数据不完整：待确认和分类不属于同一个账本")
            }
            if (draft.status.isBlank()) fail("备份里有不完整的待确认")
            val key = draft.notificationKey
            if (key != null && !notificationKeys.add(key)) fail("备份里有重复的通知草稿")
        }
        return BackupSummary(
            schemaVersion = document.schemaVersion,
            createdAtEpochMillis = document.createdAtEpochMillis,
            ledgerCount = tables.ledgers.size,
            accountCount = tables.accounts.size,
            transactionCount = tables.transactions.size,
        )
    }

    private fun uniqueIds(ids: List<String>, label: String): Set<String> {
        if (ids.any { it.isBlank() }) fail("备份里有缺少编号的$label")
        if (ids.size != ids.toSet().size) fail("备份里有重复的$label")
        return ids.toSet()
    }

    private inline fun <reified T : Enum<T>> enumOk(value: String): Boolean =
        runCatching { enumValueOf<T>(value) }.isSuccess

    private fun fail(message: String): Nothing = throw BackupFailure(message)
}
