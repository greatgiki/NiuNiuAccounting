package com.example.localledger.core.storage

import android.util.AtomicFile
import android.util.Log
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
    val keepAmountlessDrafts: Boolean = true,
    val importAccountIdBySource: Map<String, String> = emptyMap(),
    val appLockTimeoutMinutes: Int = 1,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String? = null,
    val autoBackupKeepCount: Int = 5,
    /** When the app lock is on, the home-screen widget replaces amounts with dots. */
    val widgetMaskAmountsWhenLocked: Boolean = true,
    /** 云闪付页面识别。旧设置文件没有这一项时默认打开，总开关仍是 [autoRecordEnabled]。 */
    val unionPayEnabled: Boolean = true,
    /** 银行短信。默认关闭，需要用户单独允许。 */
    val bankSmsEnabled: Boolean = false,
    /** 无障碍没读到页面时，继续用原来的通知解析。 */
    val notificationFallbackEnabled: Boolean = true,
    /** 空字符串表示用当前账本。 */
    val captureLedgerId: String = "",
    val captureAccountId: String = "",
    val captureExpenseCategoryId: String = "",
    val captureIncomeCategoryId: String = "",
    val captureTransferAccountId: String = "",
    /** ledger|TYPE|merchantKey -> accountId */
    val learnedAccountByKey: Map<String, String> = emptyMap(),
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

    @Volatile
    private var memory: AppSettings? = null

    suspend fun peek(): AppSettings? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readQuietly()?.also { memory = it } ?: memory
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
            memory = settings
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
            transform(current).also {
                write(atomicFile, it)
                memory = it
            }
        }
    }

    private fun readQuietly(): AppSettings? {
        val file = storageManager.ensureDirectories().settingsFile
        if (!file.exists()) return null
        return try {
            AtomicFile(file).openRead().use { input ->
                json.decodeFromString<AppSettings>(input.bufferedReader().readText())
            }
        } catch (error: Exception) {
            Log.w("LocalLedger", "读取设置失败，继续使用最近一次成功读取的结果", error)
            null
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
