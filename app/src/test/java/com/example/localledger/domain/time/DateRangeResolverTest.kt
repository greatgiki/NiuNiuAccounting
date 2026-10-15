package com.example.localledger.domain.time

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.StatisticsPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeResolverTest {
    private val resolver = DateRangeResolver { ZoneId.of("Asia/Shanghai") }

    @Test
    fun resolvesDayAsHalfOpenDeviceZoneRange() {
        val range = resolver.resolve(StatisticsPeriod.Day(LocalDate.of(2026, 7, 21)))

        assertEquals(Instant.parse("2026-07-20T16:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-07-21T16:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun resolvesMonthAndIncludesLastDay() {
        val range = resolver.resolve(StatisticsPeriod.Month(YearMonth.of(2026, 2)))

        assertEquals(Instant.parse("2026-01-31T16:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-02-28T16:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun customRangeIncludesEntireEndDate() {
        val range = resolver.resolve(
            StatisticsPeriod.Custom(
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 21),
            ),
        )

        assertEquals(Instant.parse("2026-06-30T16:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-07-21T16:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun oneSidedStartIsOpenEnded() {
        val range = resolver.resolveOptional(LocalDate.of(2026, 7, 21), null)!!

        assertEquals(Instant.parse("2026-07-20T16:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Long.MAX_VALUE, range.endExclusive)
    }

    @Test
    fun oneSidedEndIncludesTheWholeEndDate() {
        val range = resolver.resolveOptional(null, LocalDate.of(2026, 7, 21))!!

        assertEquals(Long.MIN_VALUE, range.startInclusive)
        assertEquals(Instant.parse("2026-07-21T16:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun missingBoundsDoNotCreateARange() {
        assertEquals(null, resolver.resolveOptional(null, null))
    }

    @Test
    fun bothBoundsMatchTheCustomRange() {
        val start = LocalDate.of(2026, 7, 1)
        val end = LocalDate.of(2026, 7, 21)

        assertEquals(
            resolver.resolve(StatisticsPeriod.Custom(start, end)),
            resolver.resolveOptional(start, end),
        )
    }

    @Test(expected = LedgerException.InvalidDateRange::class)
    fun rejectsReversedOptionalRange() {
        resolver.resolveOptional(LocalDate.of(2026, 7, 22), LocalDate.of(2026, 7, 21))
    }

    @Test(expected = LedgerException.InvalidDateRange::class)
    fun rejectsReversedCustomRange() {
        resolver.resolve(
            StatisticsPeriod.Custom(
                startDate = LocalDate.of(2026, 7, 22),
                endDate = LocalDate.of(2026, 7, 21),
            ),
        )
    }
}
