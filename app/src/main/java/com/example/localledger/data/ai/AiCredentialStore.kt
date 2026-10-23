package com.example.localledger.data.ai

import com.example.localledger.security.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * API key ciphertext in the app's private files directory.
 * The plaintext is never logged, never written into settings or diagnostics,
 * and the file is excluded from backup and device transfer.
 */
sealed interface CredentialRead {
    data object Absent : CredentialRead
    class Present internal constructor(val key: String) : CredentialRead {
        override fun toString(): String = "Present"
    }
    data object Unreadable : CredentialRead
}

class AiCredentialStore(
    private val file: File,
    private val secretBox: SecretBox,
) {
    private val mutex = Mutex()

    suspend fun read(): CredentialRead = mutex.withLock {
        withContext(Dispatchers.IO) { readLocked() }
    }

    suspend fun write(key: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                deleteLocked()
                return@withContext
            }
            val plain = trimmed.toByteArray(Charsets.UTF_8)
            try {
                val sealed = secretBox.seal(plain)
                writeAtomic(sealed)
            } finally {
                plain.fill(0)
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { deleteLocked() }
    }

    private fun readLocked(): CredentialRead {
        if (!file.exists() || file.length() == 0L) return CredentialRead.Absent
        return try {
            val plain = secretBox.open(file.readBytes())
            try {
                val text = plain.toString(Charsets.UTF_8).trim()
                if (text.isEmpty()) CredentialRead.Absent else CredentialRead.Present(text)
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            CredentialRead.Unreadable
        }
    }

    private fun deleteLocked() {
        if (file.exists()) file.delete()
    }

    private fun writeAtomic(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (file.exists() && !file.delete()) throw java.io.IOException("replace")
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "ai-credential.bin"
    }
}
