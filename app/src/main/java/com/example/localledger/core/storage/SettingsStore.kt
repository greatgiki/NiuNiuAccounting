package com.example.localledger.core.storage

import android.util.AtomicFile
import com.example.localledger.domain.error.LedgerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val currentLedgerId: String,
    val lastAccountIdByLedger: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hasAcknowledgedDataLoss: Boolean = false,
    val autoRecordEnabled: Boolean = false,
    val autoConfirmDrafts: Boolean = false,
    val wechatNotificationEnabled: Boolean = true,
    val alipayNotificationEnabled: Boolean = true,
    val importAccountIdBySource: Map<String, String> = emptyMap(),
) {
    fun importAccountKey(ledgerId: String, source: String) = "$ledgerId|$source"

    fun importAccountId(ledgerId: String, source: String): String? =
        importAccountIdBySource[importAccountKey(ledgerId, source)]
}

@Singleton
class SettingsStore @Inject constructor(
    private val storageManager: LocalStorageManager,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun peek(): AppSettings? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = storageManager.ensureDirectories().settingsFile
            if (!file.exists()) return@withContext null
            try {
                AtomicFile(file).openRead().use { input ->
                    json.decodeFromString<AppSettings>(input.bufferedReader().readText())
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun readOrCreate(defaultLedgerId: String): AppSettings = mutex.withLock {
        withContext(Dispatchers.IO) {
            val atomicFile = AtomicFile(storageManager.ensureDirectories().settingsFile)
            val settings = try {
                atomicFile.openRead().use { input ->
                    json.decodeFromString<AppSettings>(input.bufferedReader().readText())
                }
            } catch (_: FileNotFoundException) {
                AppSettings(defaultLedgerId)
            } catch (_: Exception) {
                AppSettings(defaultLedgerId)
            }
            write(atomicFile, settings)
            settings
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = mutex.withLock {
        withContext(Dispatchers.IO) {
            val atomicFile = AtomicFile(storageManager.ensureDirectories().settingsFile)
            val current = try {
                atomicFile.openRead().use { input ->
                    json.decodeFromString<AppSettings>(input.bufferedReader().readText())
                }
            } catch (error: Exception) {
                throw LedgerException.LocalStorage(error)
            }
            transform(current).also { write(atomicFile, it) }
        }
    }

    private fun write(file: AtomicFile, settings: AppSettings) {
        val output = file.startWrite()
        try {
            output.write(json.encodeToString(settings).toByteArray(Charsets.UTF_8))
            output.flush()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw LedgerException.LocalStorage(error)
        }
    }
}
