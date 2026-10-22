package com.example.localledger.domain.time

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.StatisticsPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

fun interface ZoneIdProvider {
    fun current(): ZoneId
}

class SystemZoneIdProvider @Inject constructor() : ZoneIdProvider {
    override fun current(): ZoneId = ZoneId.systemDefault()
}

class DateRangeResolver @Inject constructor(
    private val zoneIdProvider: ZoneIdProvider,
) {
    fun resolve(period: StatisticsPeriod): DateTimeRange = when (period) {
        is StatisticsPeriod.Day -> range(period.date, period.date.plusDays(1))
        is StatisticsPeriod.Week -> {
            val start = period.weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            range(start, start.plusWeeks(1))
        }
        is StatisticsPeriod.Month -> range(
            period.yearMonth.atDay(1),
            period.yearMonth.plusMonths(1).atDay(1),
        )
        is StatisticsPeriod.Year -> range(
            LocalDate.of(period.year, 1, 1),
            LocalDate.of(period.year + 1, 1, 1),
        )
        is StatisticsPeriod.Custom -> {
            if (period.endDate.isBefore(period.startDate)) {
                throw LedgerException.InvalidDateRange()
            }
            range(period.startDate, period.endDate.plusDays(1))
        }
    }

    fun year(year: Int): DateTimeRange = resolve(StatisticsPeriod.Year(year))

    fun resolveOptional(startDate: LocalDate?, endDate: LocalDate?): DateTimeRange? {
        if (startDate == null && endDate == null) return null
        if (startDate != null && endDate != null) {
            return resolve(StatisticsPeriod.Custom(startDate, endDate))
        }
        if (startDate != null) {
            return DateTimeRange(
                startInclusive = resolve(StatisticsPeriod.Day(startDate)).startInclusive,
                endExclusive = Long.MAX_VALUE,
            )
        }
        val end = endDate ?: return null
        return DateTimeRange(
            startInclusive = Long.MIN_VALUE,
            endExclusive = resolve(StatisticsPeriod.Day(end)).endExclusive,
        )
    }

    fun zoneId(): ZoneId = zoneIdProvider.current()

    private fun range(start: LocalDate, endExclusive: LocalDate): DateTimeRange {
        val zone = zoneIdProvider.current()
        return DateTimeRange(
            startInclusive = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}

