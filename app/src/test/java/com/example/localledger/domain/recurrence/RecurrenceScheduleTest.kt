package com.example.localledger.domain.recurrence

import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceScheduleTest {
    private val start = LocalDate.of(2026, 1, 31)

    @Test
    fun monthlyKeepsTheOriginalDayInsteadOfDrifting() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceSchedule.occurrence(start, RecurrencePeriod.MONTHLY, 1))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceSchedule.occurrence(start, RecurrencePeriod.MONTHLY, 2))
        assertEquals(
            LocalDate.of(2026, 3, 31),
            RecurrenceSchedule.nextAfter(start, RecurrencePeriod.MONTHLY, LocalDate.of(2026, 2, 28)),
        )
    }

    @Test
    fun weeklyAndYearlyAdvanceFromTheStartDate() {
        assertEquals(LocalDate.of(2026, 2, 7), RecurrenceSchedule.occurrence(start, RecurrencePeriod.WEEKLY, 1))
        assertEquals(LocalDate.of(2027, 1, 31), RecurrenceSchedule.occurrence(start, RecurrencePeriod.YEARLY, 1))
        assertEquals(LocalDate.of(2026, 2, 1), RecurrenceSchedule.occurrence(start, RecurrencePeriod.DAILY, 1))
    }

    @Test
    fun dueDatesStopAtTodayAndEndDateAndSkipPausedRules() {
        val rule = sample(next = start, end = LocalDate.of(2026, 2, 2), paused = false)
        assertEquals(
            listOf(start, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)),
            RecurrenceSchedule.dueDates(rule, LocalDate.of(2026, 2, 10)),
        )
        assertEquals(emptyList<LocalDate>(), RecurrenceSchedule.dueDates(rule.copy(paused = true), LocalDate.of(2026, 2, 10)))
    }

    @Test
    fun futureNextRunProducesNothing() {
        val rule = sample(next = LocalDate.of(2026, 3, 1), end = null, paused = false)
        assertEquals(emptyList<LocalDate>(), RecurrenceSchedule.dueDates(rule, LocalDate.of(2026, 2, 1)))
    }

    private fun sample(next: LocalDate, end: LocalDate?, paused: Boolean) = RecurringRule(
        id = "rule",
        ledgerId = "ledger",
        type = TransactionType.EXPENSE,
        amountMinor = 100,
        toAmountMinor = null,
        accountId = "cash",
        toAccountId = null,
        categoryId = "food",
        note = null,
        period = RecurrencePeriod.DAILY,
        startDate = start,
        endDate = end,
        nextRunDate = next,
        paused = paused,
        createdAt = 1,
        updatedAt = 1,
    )
}
