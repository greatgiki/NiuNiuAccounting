package com.example.localledger.core.money

import com.example.localledger.domain.error.LedgerException
import java.math.BigDecimal
import java.math.RoundingMode

object MoneyParser {
    fun yuanToMinor(input: String): Long {
        val normalized = input.trim()
        if (normalized.isEmpty()) throw LedgerException.InvalidAmount()

        return try {
            val yuan = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
            val minor = yuan.movePointRight(2).longValueExact()
            requirePositive(minor)
            minor
        } catch (error: ArithmeticException) {
            throw LedgerException.InvalidAmount()
        } catch (error: NumberFormatException) {
            throw LedgerException.InvalidAmount()
        }
    }

    fun toMinor(input: String, minorDigits: Int): Long {
        val minor = toSignedMinor(input, minorDigits)
        requirePositive(minor)
        return minor
    }

    fun toSignedMinor(input: String, minorDigits: Int): Long {
        val normalized = input.trim()
        if (normalized.isEmpty() || minorDigits !in 0..4) throw LedgerException.InvalidAmount()
        return try {
            val value = BigDecimal(normalized).setScale(minorDigits, RoundingMode.UNNECESSARY)
            value.movePointRight(minorDigits).longValueExact()
        } catch (error: ArithmeticException) {
            throw LedgerException.InvalidAmount()
        } catch (error: NumberFormatException) {
            throw LedgerException.InvalidAmount()
        }
    }

    fun requirePositive(amountMinor: Long) {
        if (amountMinor <= 0) throw LedgerException.InvalidAmount()
    }
}

