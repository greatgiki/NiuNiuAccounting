package com.example.localledger.domain.capture

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PaymentTimes {
    private val full = Regex("""(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val monthDay = Regex("""(\d{1,2})月(\d{1,2})日(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    fun parse(text: String, now: Long, zone: ZoneId): Long? {
        val compact = text.replace(" ", "").replace("\n", "")
        full.find(compact)?.let { match ->
            return epoch(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
                hour = match.groupValues[4].toInt(),
                minute = match.groupValues[5].toInt(),
                second = match.groupValues.getOrNull(6)?.toIntOrNull() ?: 0,
                zone = zone,
            )
        }
        val match = monthDay.find(compact) ?: return null
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        val year = if (candidate.isAfter(today.plusDays(2))) today.year - 1 else today.year
        return epoch(
            year = year,
            month = month,
            day = day,
            hour = match.groupValues[3].toInt(),
            minute = match.groupValues[4].toInt(),
            second = match.groupValues.getOrNull(5)?.toIntOrNull() ?: 0,
            zone = zone,
        )
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zone: ZoneId): Long? {
        return runCatching {
            LocalDate.of(year, month, day)
                .atTime(hour, minute, second)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
