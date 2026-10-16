package com.example.localledger.ui.common

import com.example.localledger.domain.money.CurrencyCatalog
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

    fun format(amountMinor: Long, currencyCode: String = "CNY"): String {
        val spec = CurrencyCatalog.of(currencyCode)
        val negative = amountMinor < 0
        val abs = BigInteger.valueOf(amountMinor).abs()
        val factor = BigInteger.TEN.pow(spec.minorDigits)
        val major = if (spec.minorDigits == 0) abs else abs.divide(factor)
        val fraction = if (spec.minorDigits == 0) 0 else abs.remainder(factor).toInt()
        val sign = if (negative) "-" else ""
        val digits = if (spec.minorDigits == 0) {
            "$major"
        } else {
            "$major.${fraction.toString().padStart(spec.minorDigits, '0')}"
        }
        return "$sign${spec.symbol}$digits"
    }

    fun formatSigned(amountMinor: Long, positive: Boolean, currencyCode: String = "CNY"): String {
        val body = format(amountMinor, currencyCode).removePrefix("-")
        return (if (positive) "+" else "-") + body
    }

    fun minorToInput(amountMinor: Long, currencyCode: String = "CNY"): String {
        val digits = CurrencyCatalog.of(currencyCode).minorDigits
        val abs = BigInteger.valueOf(amountMinor).abs()
        val factor = BigInteger.TEN.pow(digits)
        val major = if (digits == 0) abs else abs.divide(factor)
        val fraction = if (digits == 0) 0 else abs.remainder(factor).toInt()
        val sign = if (amountMinor < 0) "-" else ""
        if (digits == 0 || fraction == 0) return "$sign$major"
        val fractionText = fraction.toString().padStart(digits, '0').trimEnd('0')
        return "$sign$major.$fractionText"
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
