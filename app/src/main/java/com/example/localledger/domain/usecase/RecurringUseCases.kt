package com.example.localledger.domain.usecase

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.recurrence.RecurrenceSchedule
import com.example.localledger.domain.recurrence.RecurringIds
import com.example.localledger.domain.repository.RecurringRuleRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.ZoneIdProvider
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveRecurringRuleUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    suspend operator fun invoke(rule: RecurringRule) = repository.save(rule)
}

class DeleteRecurringRuleUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ledgerId: String, ruleId: String) = repository.delete(ledgerId, ruleId)
}

class SetRecurringRulePausedUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ledgerId: String, ruleId: String, paused: Boolean) {
        repository.setPaused(ledgerId, ruleId, paused)
    }
}

class GenerateRecurringTransactionsUseCase @Inject constructor(
    private val rules: RecurringRuleRepository,
    private val transactions: TransactionRepository,
    private val saveIncomeExpense: SaveIncomeExpenseUseCase,
    private val saveTransfer: SaveTransferUseCase,
    private val zoneIdProvider: ZoneIdProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock.withZone(zoneIdProvider.current()))): Int {
        var created = 0
        for (rule in rules.listActive()) {
            try {
                created += generate(rule, today)
            } catch (error: CancellationException) {
                throw error
            } catch (_: LedgerException) {
                // 单条规则失败不影响其他规则，下次启动再试。
            }
        }
        return created
    }

    private suspend fun generate(rule: RecurringRule, today: LocalDate): Int {
        val due = RecurrenceSchedule.dueDates(rule, today)
        var created = 0
        var cursor = if (rule.nextRunDate.isBefore(rule.startDate)) rule.startDate else rule.nextRunDate
        for (date in due) {
            if (!rules.hasOccurrence(rule.id, date)) {
                val transactionId = RecurringIds.transactionId(rule.id, date)
                if (!transactions.contains(transactionId)) {
                    val command = commandFor(rule, date, transactionId)
                    if (rule.type == TransactionType.TRANSFER) {
                        saveTransfer(command)
                    } else {
                        saveIncomeExpense(command)
                    }
                    created += 1
                }
                rules.recordOccurrence(rule.id, date, transactionId)
            }
            cursor = RecurrenceSchedule.nextAfter(rule.startDate, rule.period, date)
        }
        if (due.isNotEmpty()) {
            rules.updateNextRun(rule.id, cursor)
        }
        return created
    }

    private fun commandFor(rule: RecurringRule, date: LocalDate, transactionId: String): SaveTransactionCommand {
        val zone = zoneIdProvider.current()
        val at = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return SaveTransactionCommand(
            transactionId = transactionId,
            ledgerId = rule.ledgerId,
            type = rule.type,
            amountMinor = rule.amountMinor,
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            categoryId = rule.categoryId,
            transactionAt = at,
            note = rule.note,
            toAmountMinor = rule.toAmountMinor,
        )
    }
}
