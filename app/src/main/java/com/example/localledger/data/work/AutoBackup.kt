package com.example.localledger.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.localledger.data.backup.LedgerBackupRepository
import com.example.localledger.ui.AppSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

object AutoBackupRetention {
    const val PREFIX = "牛牛记账自动备份-"

    fun staleNames(files: List<Pair<String, Long>>, keep: Int): List<String> {
        val safeKeep = keep.coerceAtLeast(1)
        return files
            .filter { (name, _) -> name.startsWith(PREFIX) }
            .sortedByDescending { (_, modified) -> modified }
            .drop(safeKeep)
            .map { (name, _) -> name }
    }
}

@Singleton
class AutoBackupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: LedgerBackupRepository,
    private val appSession: AppSession,
    private val clock: Clock,
) {
    suspend fun setEnabled(enabled: Boolean) {
        val settings = appSession.updateSettings { it.copy(autoBackupEnabled = enabled) }
        AutoBackupScheduler.sync(context, settings.autoBackupEnabled && !settings.autoBackupTreeUri.isNullOrBlank())
        if (enabled && !settings.autoBackupTreeUri.isNullOrBlank()) {
            AutoBackupScheduler.enqueueSoon(context)
        }
    }

    suspend fun setFolder(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val settings = appSession.updateSettings {
            it.copy(autoBackupEnabled = true, autoBackupTreeUri = uri.toString())
        }
        AutoBackupScheduler.sync(context, true)
        if (settings.autoBackupEnabled) AutoBackupScheduler.enqueueSoon(context)
    }

    suspend fun setKeepCount(count: Int) {
        val clamped = count.coerceIn(1, 20)
        appSession.updateSettings { it.copy(autoBackupKeepCount = clamped) }
    }

    fun folderLabel(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "已选择的文件夹"
    }

    suspend fun run(iterations: Int = com.example.localledger.data.backup.BackupCrypto.DEFAULT_ITERATIONS): Boolean {
        val settings = appSession.state.value.settings ?: return false
        if (!settings.autoBackupEnabled) return true
        val uriString = settings.autoBackupTreeUri ?: return true
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
            ?: throw IllegalStateException("无法打开自动备份文件夹")
        val bytes = backupRepository.exportBytes(password = null, iterations = iterations)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(clock.millis()))
        val name = AutoBackupRetention.PREFIX + stamp + ".nnbk"
        val created = tree.createFile("application/octet-stream", name)
            ?: throw IllegalStateException("无法在所选文件夹创建备份")
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IllegalStateException("无法写入自动备份")
        val ranked = tree.listFiles().mapNotNull { file ->
            val fileName = file.name ?: return@mapNotNull null
            fileName to file.lastModified()
        }
        val stale = AutoBackupRetention.staleNames(ranked, settings.autoBackupKeepCount).toSet()
        tree.listFiles().forEach { file ->
            if (file.name in stale && file.uri != created.uri) file.delete()
        }
        return true
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoBackupEntryPoint::class.java,
        ).coordinator()
        return try {
            if (coordinator.run()) Result.success() else Result.retry()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("LocalLedger", "自动备份失败", error)
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun coordinator(): AutoBackupCoordinator
}

object AutoBackupScheduler {
    private const val PERIODIC = "ledger-auto-backup"
    private const val SOON = "ledger-auto-backup-soon"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun enqueueSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }
}
