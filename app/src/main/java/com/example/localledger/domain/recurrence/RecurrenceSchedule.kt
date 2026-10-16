package com.example.localledger.domain.recurrence

import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import java.time.LocalDate
import java.util.UUID

object RecurringIds {
    fun transactionId(ruleId: String, date: LocalDate): String =
        UUID.nameUUIDFromBytes("$ruleId|$date".toByteArray(Charsets.UTF_8)).toString()
}

object RecurrenceSchedule {
    const val MAX_OCCURRENCES_PER_RUN = 500

    fun occurrence(start: LocalDate, period: RecurrencePeriod, index: Int): LocalDate {
        require(index >= 0)
        return when (period) {
            RecurrencePeriod.DAILY -> start.plusDays(index.toLong())
            RecurrencePeriod.WEEKLY -> start.plusWeeks(index.toLong())
            RecurrencePeriod.MONTHLY -> start.plusMonths(index.toLong())
            RecurrencePeriod.YEARLY -> start.plusYears(index.toLong())
        }
    }

    /** 严格晚于 [generated] 的下一次发生日，从起始日按周期推算，避免月末逐次滚动造成日期漂移。 */
    fun nextAfter(start: LocalDate, period: RecurrencePeriod, generated: LocalDate): LocalDate {
        var index = 0
        var cursor = start
        while (!cursor.isAfter(generated)) {
            index += 1
            if (index > 20_000) return generated.plusDays(1)
            cursor = occurrence(start, period, index)
        }
        return cursor
    }

    fun dueDates(rule: RecurringRule, today: LocalDate, limit: Int = MAX_OCCURRENCES_PER_RUN): List<LocalDate> {
        if (rule.paused) return emptyList()
        val end = rule.endDate
        var cursor = if (rule.nextRunDate.isBefore(rule.startDate)) rule.startDate else rule.nextRunDate
        if (end != null && cursor.isAfter(end)) return emptyList()
        val dates = ArrayList<LocalDate>(8)
        var guard = 0
        while (!cursor.isAfter(today) && (end == null || !cursor.isAfter(end)) && guard < limit) {
            if (!cursor.isBefore(rule.startDate)) dates += cursor
            cursor = nextAfter(rule.startDate, rule.period, cursor)
            guard += 1
        }
        return dates
    }
}
