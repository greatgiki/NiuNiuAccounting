package com.example.localledger.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    const val DEFAULT_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun salt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, iterations: Int, expected: ByteArray): Boolean {
        val actual = hash(pin, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }
}

object PinPolicy {
    fun check(pin: String, confirm: String): String? {
        if (pin.length !in 4..6 || pin.any { !it.isDigit() }) return "请设置 4 到 6 位数字密码"
        if (pin != confirm) return "两次输入不一致"
        return null
    }
}

fun shouldRelock(hasPin: Boolean, timeoutMinutes: Int, backgroundedAt: Long, now: Long): Boolean {
    if (!hasPin) return false
    val limit = timeoutMinutes.coerceAtLeast(0) * 60_000L
    return now - backgroundedAt >= limit
}
