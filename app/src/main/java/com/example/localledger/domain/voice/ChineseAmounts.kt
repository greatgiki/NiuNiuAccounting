package com.example.localledger.domain.voice

import java.math.BigDecimal
import java.math.RoundingMode

internal data class AmountMatch(
    val amount: BigDecimal,
    val start: Int,
    val endExclusive: Int,
    val explicit: Boolean,
)

/**
 * Spoken Chinese money, kept local and allocation-light.
 * Colloquial tails drop a unit: 一百二 = 120, 两千五 = 2500, 一万二 = 12000.
 */
internal object ChineseAmounts {
    private val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    private val numeralChars = buildSet {
        addAll(digits.keys)
        addAll(listOf('十', '百', '千', '万', '亿', '点'))
    }
    private val scaleOf = mapOf(
        '十' to 10L,
        '百' to 100L,
        '千' to 1_000L,
        '万' to 10_000L,
        '亿' to 100_000_000L,
    )
    private val maxYuan = BigDecimal("1000000000")

    private data class NumberRead(
        val value: BigDecimal,
        val endExclusive: Int,
        val lastUnit: Long,
        val hasUnit: Boolean,
    )

    fun find(text: String): AmountMatch? {
        val found = ArrayList<AmountMatch>(2)
        var index = 0
        while (index < text.length) {
            val match = parseAt(text, index)
            if (match != null) {
                found += match
                index = match.endExclusive
            } else {
                index++
            }
        }
        return found.firstOrNull { it.explicit } ?: found.firstOrNull()
    }

    private fun parseAt(text: String, start: Int): AmountMatch? {
        if (start > 0) {
            val previous = text[start - 1]
            if (previous.isDigit() || previous in numeralChars || previous == '.') return null
        }
        val number = readNumber(text, start) ?: return null
        var end = skipSpaces(text, number.endExclusive)
        var value = number.value
        var explicit = number.hasUnit
        var unit = number.lastUnit

        if (unit == 0L && end < text.length && text[end] in scaleOf) {
            val scale = scaleOf.getValue(text[end])
            value = value.multiply(BigDecimal.valueOf(scale))
            unit = scale
            explicit = true
            end = skipSpaces(text, end + 1)
            if (scale >= 100) {
                val tail = readSingleDigit(text, end)
                if (tail != null && tail.digit != 0 && !followedByDateMarker(text, tail.endExclusive)) {
                    value = value.add(BigDecimal.valueOf(tail.digit.toLong() * (scale / 10)))
                    end = skipSpaces(text, tail.endExclusive)
                }
            }
        }

        if (end < text.length && (text[end] == '毛' || text[end] == '角')) {
            var fractionEnd = skipSpaces(text, end + 1)
            var fen = BigDecimal.ZERO
            val fenDigit = readSingleDigit(text, fractionEnd)
            if (fenDigit != null) {
                fen = BigDecimal(fenDigit.digit).movePointLeft(2)
                fractionEnd = fenDigit.endExclusive
                if (fractionEnd < text.length && text[fractionEnd] == '分') fractionEnd++
            }
            value = value.movePointLeft(1).add(fen)
            return finish(value, start, fractionEnd, explicit = true)
        }
        if (end < text.length && text[end] == '分' && !number.hasUnit && unit == 0L) {
            return finish(value.movePointLeft(2), start, end + 1, explicit = true)
        }

        var sawYuan = false
        if (text.startsWith("块钱", end)) {
            end += 2
            sawYuan = true
        } else if (end < text.length && text[end] in "块元圆") {
            end++
            sawYuan = true
        }
        if (sawYuan) {
            explicit = true
            end = skipSpaces(text, end)
            if (end < text.length && text[end] == '半') {
                value = value.add(BigDecimal("0.5"))
                end++
            } else {
                val fraction = readFractionAfterYuan(text, end)
                if (fraction != null) {
                    value = value.add(fraction.value)
                    end = fraction.endExclusive
                }
            }
        }
        if (!explicit && end < text.length && text[end] in "月日号") return null
        return finish(value, start, end, explicit)
    }

    private fun finish(value: BigDecimal, start: Int, endExclusive: Int, explicit: Boolean): AmountMatch? {
        if (endExclusive <= start) return null
        if (value.signum() <= 0 || value.compareTo(maxYuan) > 0) return null
        val amount = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        if (amount.signum() <= 0) return null
        if (!explicit) {
            val digits = amount.toPlainString().substringBefore('.').trimStart('-')
            if (digits.length > 7) return null
        }
        return AmountMatch(amount, start, endExclusive, explicit)
    }

    private data class Fraction(val value: BigDecimal, val endExclusive: Int)

    private fun readFractionAfterYuan(text: String, start: Int): Fraction? {
        val index = skipSpaces(text, start)
        val digit = readSingleDigit(text, index) ?: return null
        var end = skipSpaces(text, digit.endExclusive)
        if (end < text.length && (text[end] == '毛' || text[end] == '角')) {
            end = skipSpaces(text, end + 1)
            val fen = readSingleDigit(text, end)
            return if (fen != null) {
                var fenEnd = fen.endExclusive
                if (fenEnd < text.length && text[fenEnd] == '分') fenEnd++
                val amount = BigDecimal(digit.digit).movePointLeft(1)
                    .add(BigDecimal(fen.digit).movePointLeft(2))
                Fraction(amount, fenEnd)
            } else {
                Fraction(BigDecimal(digit.digit).movePointLeft(1), end)
            }
        }
        if (end < text.length && text[end] == '分') {
            return Fraction(BigDecimal(digit.digit).movePointLeft(2), end + 1)
        }
        return Fraction(BigDecimal(digit.digit).movePointLeft(1), digit.endExclusive)
    }

    private data class DigitRead(val digit: Int, val endExclusive: Int)

    private fun readSingleDigit(text: String, index: Int): DigitRead? {
        if (index >= text.length) return null
        val ch = text[index]
        val digit = when {
            ch in '0'..'9' -> ch - '0'
            ch in digits -> digits.getValue(ch)
            else -> return null
        }
        if (ch.isDigit() && index + 1 < text.length && text[index + 1].isDigit()) return null
        if (index + 1 < text.length && text[index + 1] in "十百千万亿") return null
        return DigitRead(digit, index + 1)
    }

    private fun followedByDateMarker(text: String, index: Int): Boolean {
        val next = skipSpaces(text, index)
        return next < text.length && text[next] in "月日号"
    }

    private fun readNumber(text: String, start: Int): NumberRead? {
        if (start >= text.length) return null
        val ch = text[start]
        return when {
            ch.isDigit() -> readArabic(text, start)
            ch in numeralChars && ch != '点' -> readChinese(text, start)
            else -> null
        }
    }

    private fun readArabic(text: String, start: Int): NumberRead? {
        var end = start
        while (end < text.length && text[end].isDigit()) end++
        if (end < text.length && text[end] == '.' && end + 1 < text.length && text[end + 1].isDigit()) {
            end++
            while (end < text.length && text[end].isDigit()) end++
        }
        if (end == start) return null
        val token = text.substring(start, end)
        val value = token.toBigDecimalOrNull() ?: return null
        return NumberRead(value, end, lastUnit = 0, hasUnit = false)
    }

    private fun readChinese(text: String, start: Int): NumberRead? {
        var end = start
        while (end < text.length && text[end] in numeralChars) end++
        if (end == start) return null
        val parsed = parseChinese(text.substring(start, end)) ?: return null
        return NumberRead(parsed.value, end, parsed.lastUnit, parsed.hasUnit)
    }

    private data class Parsed(val value: BigDecimal, val lastUnit: Long, val hasUnit: Boolean)

    private fun parseChinese(token: String): Parsed? {
        val dot = token.indexOf('点')
        if (dot >= 0) {
            if (token.indexOf('点', dot + 1) >= 0) return null
            val head = token.substring(0, dot)
            val tail = token.substring(dot + 1)
            if (tail.isEmpty() || tail.any { digits[it] == null }) return null
            val integer = when {
                head.isEmpty() || head.all { it == '零' || it == '〇' } -> BigDecimal.ZERO
                else -> parseInteger(head)?.value ?: return null
            }
            val fraction = tail.map { digits.getValue(it) }.joinToString("")
            return Parsed(BigDecimal("$integer.$fraction"), lastUnit = 0, hasUnit = false)
        }
        return parseInteger(token)
    }

    private fun parseInteger(token: String): Parsed? {
        if (token.isEmpty()) return null
        return try {
            var total = 0L
            var section = 0L
            var number = 0
            var hasDigit = false
            var lastUnit = 0L
            var sawZero = false
            var seenUnit = false
            for (ch in token) {
                val digit = digits[ch]
                if (digit != null) {
                    if (digit == 0) sawZero = true
                    number = digit
                    hasDigit = true
                    continue
                }
                val small = when (ch) {
                    '十' -> 10L
                    '百' -> 100L
                    '千' -> 1_000L
                    else -> null
                }
                if (small != null) {
                    val multiplier = if (!hasDigit) 1 else number
                    section = Math.addExact(section, Math.multiplyExact(multiplier.toLong(), small))
                    lastUnit = small
                    number = 0
                    hasDigit = false
                    sawZero = false
                    seenUnit = true
                    continue
                }
                if (ch == '万' || ch == '亿') {
                    val scale = if (ch == '万') 10_000L else 100_000_000L
                    if (hasDigit) section = Math.addExact(section, number.toLong())
                    if (section == 0L) section = 1
                    total = if (ch == '亿') {
                        Math.multiplyExact(Math.addExact(total, section), scale)
                    } else {
                        Math.addExact(total, Math.multiplyExact(section, scale))
                    }
                    section = 0
                    number = 0
                    hasDigit = false
                    lastUnit = scale
                    sawZero = false
                    seenUnit = true
                    continue
                }
                return null
            }
            if (hasDigit && number > 0) {
                val tail = if (!sawZero && lastUnit >= 100) {
                    Math.multiplyExact(number.toLong(), lastUnit / 10)
                } else {
                    number.toLong()
                }
                section = Math.addExact(section, tail)
            }
            val value = Math.addExact(total, section)
            if (value <= 0L) return null
            Parsed(BigDecimal.valueOf(value), lastUnit, seenUnit)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun skipSpaces(text: String, index: Int): Int {
        var cursor = index
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        return cursor
    }
}
