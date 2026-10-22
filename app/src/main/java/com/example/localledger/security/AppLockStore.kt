package com.example.localledger.security

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PinRecord(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
    val biometricEnabled: Boolean = false,
)

interface SecretBox {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

class KeystoreSecretBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.isEmpty()) throw IllegalArgumentException("empty")
        val ivLength = sealed[0].toInt() and 0xFF
        if (ivLength <= 0 || sealed.size <= 1 + ivLength) throw IllegalArgumentException("short")
        val iv = sealed.copyOfRange(1, 1 + ivLength)
        val body = sealed.copyOfRange(1 + ivLength, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "nn_app_lock"
    }
}

sealed interface PinRead {
    data object Absent : PinRead
    data class Present(val record: PinRecord) : PinRead
    data object Unreadable : PinRead
}

@Singleton
class AppLockStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secretBox: SecretBox,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun isConfigured(): Boolean = file.exists() && file.length() > 0L

    suspend fun read(): PinRead = mutex.withLock {
        withContext(Dispatchers.IO) { readLocked() }
    }

    suspend fun write(record: PinRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val plain = json.encodeToString(record).toByteArray(Charsets.UTF_8)
            val sealed = secretBox.seal(plain)
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(sealed)
                output.flush()
                atomic.finishWrite(output)
            } catch (error: CancellationException) {
                atomic.failWrite(output)
                throw error
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
        }
    }

    private fun readLocked(): PinRead {
        if (!file.exists() || file.length() == 0L) return PinRead.Absent
        return try {
            val sealed = AtomicFile(file).openRead().use { it.readBytes() }
            val plain = secretBox.open(sealed)
            PinRead.Present(json.decodeFromString(plain.toString(Charsets.UTF_8)))
        } catch (_: Exception) {
            PinRead.Unreadable
        }
    }

    companion object {
        const val FILE_NAME = "app-lock.bin"
    }
}

internal fun PinRecord.matches(pin: String): Boolean {
    val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
    val hash = Base64.decode(hashBase64, Base64.NO_WRAP)
    return PinHasher.verify(pin, salt, iterations, hash)
}

internal fun newPinRecord(pin: String, biometricEnabled: Boolean, iterations: Int = PinHasher.DEFAULT_ITERATIONS): PinRecord {
    val salt = PinHasher.salt()
    val hash = PinHasher.hash(pin, salt, iterations)
    return PinRecord(
        saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
        hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
        iterations = iterations,
        biometricEnabled = biometricEnabled,
    )
}
