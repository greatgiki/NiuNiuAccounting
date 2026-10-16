package com.example.localledger.domain.importing

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

object BillTimes {
    private val formatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    )

    private val dateOnly = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),
    )

    private val excelEpoch = LocalDate.of(1899, 12, 30)
    private val serialPattern = Regex("""\d{5}(?:\.\d+)?""")

    fun parse(raw: String, zoneId: ZoneId): Long? {
        val text = raw.trim().removePrefix("[").removeSuffix("]")
        if (text.isEmpty() || text == "/" || text == "／") return null
        if (serialPattern.matches(text)) {
            return excelSerialToEpoch(text.toDouble(), zoneId)
        }
        for (formatter in formatters) {
            try {
                val dateTime = LocalDateTime.parse(text, formatter)
                return dateTime.atZone(zoneId).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // try the next pattern
            }
        }
        for (formatter in dateOnly) {
            try {
                val date = LocalDate.parse(text, formatter)
                return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // try the next pattern
            }
        }
        return null
    }

    fun excelSerialToEpoch(serial: Double, zoneId: ZoneId): Long? {
        if (serial < 20_000.0 || serial > 80_000.0) return null
        val days = serial.toLong()
        val fraction = serial - days
        val date = excelEpoch.plusDays(days)
        val seconds = (fraction * 86_400.0).roundToLong().coerceIn(0L, 86_399L)
        val time = LocalTime.ofSecondOfDay(seconds)
        return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    }
}
