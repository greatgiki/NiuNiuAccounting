package com.example.localledger.domain.ai

import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.time.DateRangeResolver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ResolvedPeriod(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val label: String,
) {
    fun range(resolver: DateRangeResolver): DateTimeRange =
        resolver.resolve(StatisticsPeriod.Custom(start, endInclusive))
}

object LedgerPeriods {
    const val MAX_DAYS = 800L

    fun resolve(token: String?, startText: String?, endText: String?, today: LocalDate): ResolvedPeriod? {
        val key = token?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (key) {
            "", "today", "今天" -> day(today, "今天")
            "yesterday", "昨天" -> day(today.minusDays(1), "昨天")
            "this_week", "本周" -> week(monday(today), "本周")
            "last_week", "上周" -> week(monday(today).minusWeeks(1), "上周")
            "this_month", "本月" -> month(YearMonth.from(today), "本月")
            "last_month", "上月", "上个月" -> month(YearMonth.from(today).minusMonths(1), "上月")
            "this_year", "今年" -> year(today.year, "今年")
            "last_year", "去年" -> year(today.year - 1, "去年")
            "custom", "自定义" -> custom(startText, endText)
            else -> null
        }
    }

    private fun day(date: LocalDate, name: String) =
        ResolvedPeriod(date, date, "$name（${stamp(date)}）")

    private fun week(monday: LocalDate, name: String): ResolvedPeriod {
        val end = monday.plusDays(6)
        return ResolvedPeriod(monday, end, "$name（${stamp(monday)}–${stamp(end)}）")
    }

    private fun month(yearMonth: YearMonth, name: String) = ResolvedPeriod(
        yearMonth.atDay(1),
        yearMonth.atEndOfMonth(),
        "$name（${yearMonth.year}年${yearMonth.monthValue}月）",
    )

    private fun year(year: Int, name: String) = ResolvedPeriod(
        LocalDate.of(year, 1, 1),
        LocalDate.of(year, 12, 31),
        "$name（${year}年）",
    )

    private fun custom(startText: String?, endText: String?): ResolvedPeriod? {
        val start = parseDate(startText) ?: return null
        val end = parseDate(endText) ?: return null
        if (end.isBefore(start)) return null
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) return null
        return ResolvedPeriod(start, end, "${stamp(start)}–${stamp(end)}")
    }

    private fun monday(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim().take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun stamp(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}
