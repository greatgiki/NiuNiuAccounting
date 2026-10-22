package com.example.localledger.domain.report

import com.example.localledger.domain.model.StatisticsPeriod
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

enum class ReportKind { WEEK, MONTH }

enum class ReportLock { Unknown, Locked, Unlocked }

/** Reports stay blank until the app lock is unlocked. Unknown still counts as closed. */
fun reportContentVisible(lock: ReportLock): Boolean = lock == ReportLock.Unlocked

fun reportKindOrNull(raw: String?): ReportKind? = when (raw) {
    "week" -> ReportKind.WEEK
    "month" -> ReportKind.MONTH
    else -> null
}

fun normalizeAnchor(kind: ReportKind, date: LocalDate): LocalDate = when (kind) {
    ReportKind.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    ReportKind.MONTH -> date.withDayOfMonth(1)
}

fun initialAnchor(kind: ReportKind, raw: String?, today: LocalDate): LocalDate {
    val parsed = raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return normalizeAnchor(kind, parsed ?: today)
}

fun shiftAnchor(kind: ReportKind, anchor: LocalDate, steps: Long): LocalDate? {
    val shifted = try {
        when (kind) {
            ReportKind.WEEK -> normalizeAnchor(kind, anchor).plusWeeks(steps)
            ReportKind.MONTH -> YearMonth.from(normalizeAnchor(kind, anchor)).plusMonths(steps).atDay(1)
        }
    } catch (error: DateTimeException) {
        return null
    }
    if (shifted.year !in 1..9999) return null
    return shifted
}

fun periodBounds(kind: ReportKind, anchor: LocalDate): Pair<LocalDate, LocalDate> {
    val start = normalizeAnchor(kind, anchor)
    val end = when (kind) {
        ReportKind.WEEK -> start.plusDays(6)
        ReportKind.MONTH -> YearMonth.from(start).atEndOfMonth()
    }
    return start to end
}

fun previousPeriod(kind: ReportKind, anchor: LocalDate): Pair<LocalDate, LocalDate>? {
    val shifted = shiftAnchor(kind, anchor, -1) ?: return null
    return periodBounds(kind, shifted)
}

fun statisticsPeriod(kind: ReportKind, anchor: LocalDate): StatisticsPeriod {
    val start = normalizeAnchor(kind, anchor)
    return when (kind) {
        ReportKind.WEEK -> StatisticsPeriod.Week(start)
        ReportKind.MONTH -> StatisticsPeriod.Month(YearMonth.from(start))
    }
}

fun weekPeriodLabel(weekStart: LocalDate): String {
    require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "ISO week starts on Monday" }
    val end = weekStart.plusDays(6)
    val fields = WeekFields.ISO
    val week = weekStart.get(fields.weekOfWeekBasedYear())
    val weekYear = weekStart.get(fields.weekBasedYear())
    return "${weekYear}年第${week}周 · ${shortDateSpan(weekStart, end)}"
}

fun monthPeriodLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

fun periodLabel(kind: ReportKind, anchor: LocalDate): String = when (kind) {
    ReportKind.WEEK -> weekPeriodLabel(normalizeAnchor(kind, anchor))
    ReportKind.MONTH -> monthPeriodLabel(YearMonth.from(normalizeAnchor(kind, anchor)))
}

fun shortDateSpan(start: LocalDate, end: LocalDate): String = when {
    start == end -> "${start.monthValue}月${start.dayOfMonth}日"
    start.year == end.year && start.month == end.month ->
        "${start.monthValue}月${start.dayOfMonth}日–${end.dayOfMonth}日"
    start.year == end.year ->
        "${start.monthValue}月${start.dayOfMonth}日–${end.monthValue}月${end.dayOfMonth}日"
    else ->
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日–${end.year}年${end.monthValue}月${end.dayOfMonth}日"
}
