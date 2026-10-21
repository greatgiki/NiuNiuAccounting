package com.example.localledger.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object BackupCrypto {
    const val DEFAULT_ITERATIONS = 120_000
    const val MIN_ITERATIONS = 100
    const val MAX_ITERATIONS = 400_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    data class Sealed(
        val salt: ByteArray,
        val iv: ByteArray,
        val iterations: Int,
        val ciphertext: ByteArray,
    )

    fun seal(plain: ByteArray, password: CharArray, iterations: Int): Sealed {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = random(SALT_BYTES)
        val iv = random(IV_BYTES)
        val key = derive(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return Sealed(salt, iv, iterations, cipher.doFinal(plain))
        } finally {
            key.fill(0)
        }
    }

    fun open(sealed: Sealed, password: CharArray): ByteArray {
        if (sealed.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupFailure("备份文件已损坏或无法读取", BackupFailure.Kind.Corrupt)
        }
        if (sealed.salt.size != SALT_BYTES || sealed.iv.size != IV_BYTES) {
            throw BackupFailure("备份文件已损坏或无法读取", BackupFailure.Kind.Corrupt)
        }
        val key = derive(password, sealed.salt, sealed.iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, sealed.iv),
            )
            return cipher.doFinal(sealed.ciphertext)
        } catch (_: BackupFailure) {
            throw BackupFailure("备份文件已损坏或无法读取", BackupFailure.Kind.Corrupt)
        } catch (_: Exception) {
            throw BackupFailure("密码不正确，或文件已损坏", BackupFailure.Kind.WrongPassword)
        } finally {
            key.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun random(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
