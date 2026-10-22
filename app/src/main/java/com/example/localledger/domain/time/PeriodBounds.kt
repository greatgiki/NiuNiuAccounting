package com.example.localledger.domain.time

import com.example.localledger.domain.model.StatisticsPeriod
import java.time.LocalDate

fun StatisticsPeriod.inclusiveDates(): Pair<LocalDate, LocalDate> = when (this) {
    is StatisticsPeriod.Day -> date to date
    is StatisticsPeriod.Week -> weekStart to weekStart.plusDays(6)
    is StatisticsPeriod.Month -> yearMonth.atDay(1) to yearMonth.atEndOfMonth()
    is StatisticsPeriod.Year -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
    is StatisticsPeriod.Custom -> startDate to endDate
}
