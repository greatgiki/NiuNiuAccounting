package com.example.localledger.data.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.localledger.data.local.database.DatabaseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: DatabaseProvider,
    private val clock: Clock,
) {
    suspend fun capture(): BackupDocument = withContext(Dispatchers.IO) {
        BackupStore.read(databaseProvider.get(), clock.millis())
    }

    suspend fun exportBytes(
        password: String?,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ): ByteArray = withContext(Dispatchers.IO) {
        val document = BackupStore.read(databaseProvider.get(), clock.millis())
        encode(document, password, iterations)
    }

    suspend fun writeTo(uri: Uri, password: String?) = withContext(Dispatchers.IO) {
        val bytes = exportBytes(password)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw BackupFailure("无法写入所选位置")
    }

    suspend fun readFrom(uri: Uri, password: String?): BackupDocument = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupFailure("无法读取所选文件")
        if (bytes.size > 32 * 1024 * 1024) throw BackupFailure("备份文件过大")
        decode(bytes, password)
    }

    suspend fun restore(document: BackupDocument) = withContext(Dispatchers.IO) {
        val summary = BackupValidator.validate(document)
        val database = databaseProvider.get()
        BackupStore.restore(database, document, clock.millis()) { safety ->
            val bytes = encode(safety, password = null, iterations = BackupCrypto.DEFAULT_ITERATIONS)
            val file = File(context.filesDir, "backup/pre-restore.nnbk")
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                output.flush()
                atomic.finishWrite(output)
            } catch (error: CancellationException) {
                atomic.failWrite(output)
                throw error
            } catch (_: Exception) {
                atomic.failWrite(output)
                throw BackupFailure("无法先保存本机安全副本，已取消恢复")
            }
        }
        summary
    }

    fun preferredLedgerId(document: BackupDocument, currentLedgerId: String?): String? {
        val ledgers = document.tables.ledgers
        if (currentLedgerId != null && ledgers.any { it.id == currentLedgerId }) return currentLedgerId
        return ledgers.firstOrNull { !it.isArchived }?.id ?: ledgers.firstOrNull()?.id
    }

    private fun encode(document: BackupDocument, password: String?, iterations: Int): ByteArray {
        val chars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        return try {
            BackupCodec.encode(document, chars, iterations)
        } finally {
            chars?.fill('\u0000')
        }
    }

    private fun decode(bytes: ByteArray, password: String?): BackupDocument {
        val chars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        return try {
            BackupCodec.decode(bytes, chars)
        } finally {
            chars?.fill('\u0000')
        }
    }
}
