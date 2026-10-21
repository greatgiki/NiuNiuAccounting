package com.example.localledger.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun hashMatchesOnlyTheSamePinAndSalt() {
        val salt = PinHasher.salt()
        val hash = PinHasher.hash("1234", salt, iterations = 200)
        assertTrue(PinHasher.verify("1234", salt, 200, hash))
        assertFalse(PinHasher.verify("1235", salt, 200, hash))
        assertFalse(PinHasher.verify("1234", PinHasher.salt(), 200, hash))
        val other = PinHasher.hash("1234", PinHasher.salt(), iterations = 200)
        assertNotEquals(hash.toList(), other.toList())
    }

    @Test
    fun pinPolicyRequiresFourToSixDigitsAndMatchingConfirmation() {
        assertNull(PinPolicy.check("1234", "1234"))
        assertNull(PinPolicy.check("123456", "123456"))
        assertTrue(PinPolicy.check("123", "123")!!.contains("4 到 6"))
        assertTrue(PinPolicy.check("12ab", "12ab")!!.contains("数字"))
        assertTrue(PinPolicy.check("1234", "1235")!!.contains("不一致"))
    }

    @Test
    fun relockFollowsBackgroundTimeout() {
        assertFalse(shouldRelock(false, 0, 0L, 10_000L))
        assertTrue(shouldRelock(true, 0, 1_000L, 1_000L))
        assertFalse(shouldRelock(true, 1, 0L, 59_000L))
        assertTrue(shouldRelock(true, 1, 0L, 60_000L))
    }
}
