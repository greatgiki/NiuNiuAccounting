package com.example.localledger.domain.usecase

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.SaveTransactionCommand
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.recurrence.RecurringIds
import com.example.localledger.domain.repository.ExchangeRateRemote
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.repository.RecurringRuleRepository
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.ZoneIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RecurringAndRateUseCaseTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 3, 3)

    @Test
    fun generationIsIdempotentAndGoesThroughSaveUseCases() = runTest {
        val rules = FakeRules()
        val transactions = FakeTransactions()
        val saveIncome = SaveIncomeExpenseUseCase(transactions)
        val saveTransfer = SaveTransferUseCase(transactions)
        val generator = GenerateRecurringTransactionsUseCase(
            rules = rules,
            transactions = transactions,
            saveIncomeExpense = saveIncome,
            saveTransfer = saveTransfer,
            zoneIdProvider = ZoneIdProvider { zone },
            clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone),
        )
        rules.rules += expenseRule(LocalDate.of(2026, 3, 1))

        assertEquals(3, generator(today))
        assertEquals(3, transactions.saved.size)
        assertEquals(3, rules.occurrences.size)
        assertEquals(LocalDate.of(2026, 3, 4), rules.rules.single().nextRunDate)
        val expectedId = RecurringIds.transactionId("rule-1", LocalDate.of(2026, 3, 1))
        assertEquals(expectedId, transactions.saved.first().transactionId)

        assertEquals(0, generator(today))
        assertEquals(3, transactions.saved.size)
    }

    @Test
    fun pausedRuleIsNotGenerated() = runTest {
        val rules = FakeRules()
        val transactions = FakeTransactions()
        val generator = GenerateRecurringTransactionsUseCase(
            rules,
            transactions,
            SaveIncomeExpenseUseCase(transactions),
            SaveTransferUseCase(transactions),
            ZoneIdProvider { zone },
            Clock.fixed(Instant.parse("2026-03-03T00:00:00Z"), ZoneOffset.UTC),
        )
        rules.rules += expenseRule(LocalDate.of(2026, 3, 1)).copy(paused = true, id = "paused")
        assertEquals(0, generator(today))
        assertTrue(transactions.saved.isEmpty())
    }

    @Test
    fun refreshKeepsTheCachedSnapshotWhenTheNetworkFails() = runTest {
        val cached = ExchangeRateSnapshot("CNY", mapOf("USD" to BigDecimal("0.14")), 50L, "cache")
        val repository = FakeRates(cached)
        val useCase = RefreshExchangeRatesUseCase(
            remote = object : ExchangeRateRemote {
                override suspend fun fetch(baseCode: String): ExchangeRateSnapshot {
                    throw LedgerException.ExchangeRateUnavailable()
                }
            },
            repository = repository,
        )
        val result = useCase("CNY")
        assertTrue(result is RefreshExchangeRateResult.KeptCached)
        assertEquals(cached, (result as RefreshExchangeRateResult.KeptCached).snapshot)
    }

    @Test
    fun refreshReplacesTheCache() = runTest {
        val repository = FakeRates(null)
        val fresh = ExchangeRateSnapshot("EUR", mapOf("USD" to BigDecimal("1.08")), 90L, "Frankfurter")
        val useCase = RefreshExchangeRatesUseCase(
            remote = object : ExchangeRateRemote {
                override suspend fun fetch(baseCode: String) = fresh.copy(baseCode = baseCode)
            },
            repository = repository,
        )
        val result = useCase("EUR")
        assertTrue(result is RefreshExchangeRateResult.Updated)
        assertEquals("EUR", repository.stored?.baseCode)
        assertEquals("Frankfurter", repository.stored?.source)
    }

    private fun expenseRule(next: LocalDate) = RecurringRule(
        id = "rule-1",
        ledgerId = "ledger",
        type = TransactionType.EXPENSE,
        amountMinor = 500,
        toAmountMinor = null,
        accountId = "cash",
        toAccountId = null,
        categoryId = "food",
        note = "午餐",
        period = RecurrencePeriod.DAILY,
        startDate = next,
        endDate = null,
        nextRunDate = next,
        paused = false,
        createdAt = 1,
        updatedAt = 1,
    )
}

private class FakeRules : RecurringRuleRepository {
    val rules = mutableListOf<RecurringRule>()
    val occurrences = mutableSetOf<Pair<String, LocalDate>>()

    override fun observe(ledgerId: String): Flow<List<RecurringRule>> = flowOf(rules)
    override suspend fun save(rule: RecurringRule) = Unit
    override suspend fun setPaused(ledgerId: String, ruleId: String, paused: Boolean) = Unit
    override suspend fun delete(ledgerId: String, ruleId: String) = Unit
    override suspend fun listActive(): List<RecurringRule> = rules.filter { !it.paused }
    override suspend fun hasOccurrence(ruleId: String, occurrenceDate: LocalDate) = (ruleId to occurrenceDate) in occurrences
    override suspend fun recordOccurrence(ruleId: String, occurrenceDate: LocalDate, transactionId: String) {
        occurrences += ruleId to occurrenceDate
    }
    override suspend fun updateNextRun(ruleId: String, nextRunDate: LocalDate) {
        val index = rules.indexOfFirst { it.id == ruleId }
        rules[index] = rules[index].copy(nextRunDate = nextRunDate)
    }
}

private class FakeTransactions : TransactionRepository {
    val saved = mutableListOf<SaveTransactionCommand>()
    override fun observeTransaction(transactionId: String): Flow<TransactionDetail?> = flowOf(null)
    override fun observeTransactions(ledgerId: String, filter: TransactionFilter): Flow<List<TransactionDetail>> = flowOf(emptyList())
    override suspend fun saveTransaction(command: SaveTransactionCommand): String {
        saved += command
        return command.transactionId ?: "generated"
    }
    override suspend fun deleteTransaction(ledgerId: String, transactionId: String) = Unit
    override suspend fun contains(transactionId: String) = saved.any { it.transactionId == transactionId }
}

private class FakeRates(initial: ExchangeRateSnapshot?) : ExchangeRateRepository {
    var stored = initial
    override fun observe(baseCode: String): Flow<ExchangeRateSnapshot?> = flowOf(stored?.takeIf { it.baseCode == baseCode })
    override suspend fun get(baseCode: String) = stored?.takeIf { it.baseCode == baseCode }
    override suspend fun save(snapshot: ExchangeRateSnapshot) {
        stored = snapshot
    }
    override suspend fun baseCodesInUse() = listOf("CNY")
}
