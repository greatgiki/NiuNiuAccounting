package com.example.localledger.ui.common

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object MoneyFormatter {
    private val HUNDRED = BigInteger.valueOf(100)
    private val MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE)
    private val MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)

    fun format(amountMinor: Long): String {
        val negative = amountMinor < 0
        val abs = BigInteger.valueOf(amountMinor).abs()
        val yuan = abs.divide(HUNDRED)
        val fen = abs.remainder(HUNDRED).toInt()
        val sign = if (negative) "-" else ""
        return "$sign¥$yuan.${fen.toString().padStart(2, '0')}"
    }

    fun formatSigned(amountMinor: Long, positive: Boolean): String {
        val body = format(amountMinor).removePrefix("-")
        return (if (positive) "+" else "-") + body
    }

    fun minorToInput(amountMinor: Long): String {
        val abs = BigInteger.valueOf(amountMinor).abs()
        val yuan = abs.divide(HUNDRED)
        val fen = abs.remainder(HUNDRED).toInt()
        val sign = if (amountMinor < 0) "-" else ""
        return if (fen == 0) "$sign$yuan" else "$sign$yuan.${fen.toString().padStart(2, '0').trimEnd('0')}"
    }

    fun parseSigned(input: String): Long? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null
        return try {
            val negative = normalized.startsWith("-")
            val digits = normalized.removePrefix("-").removePrefix("+")
            val parts = digits.split(".")
            if (parts.size > 2 || parts.any { it.isNotEmpty() && !it.all(Char::isDigit) }) return null
            if (parts.size == 2 && parts[1].length > 2) return null
            val yuan = parts[0].ifEmpty { "0" }.toBigInteger()
            val fen = (parts.getOrNull(1) ?: "").padEnd(2, '0').ifEmpty { "0" }.toBigInteger()
            val minor = yuan.multiply(HUNDRED).add(fen)
            val signed = if (negative) minor.negate() else minor
            if (signed < MIN_LONG || signed > MAX_LONG) return null
            signed.toLong()
        } catch (_: Exception) {
            null
        }
    }
}

object DateTimeFormatters {
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val groupFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val dayShortFormatter = DateTimeFormatter.ofPattern("d日", Locale.CHINA)
    private val monthShortFormatter = DateTimeFormatter.ofPattern("M月", Locale.CHINA)

    fun yearMonth(yearMonth: YearMonth): String = yearMonth.format(monthFormatter)

    fun date(date: LocalDate): String = date.format(dateFormatter)

    fun groupHeader(date: LocalDate): String = date.format(groupFormatter)

    fun time(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter)

    fun dateTime(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(dateTimeFormatter)

    fun dayShort(date: LocalDate): String = date.format(dayShortFormatter)

    fun monthShort(yearMonth: YearMonth): String = yearMonth.format(monthShortFormatter)
}
