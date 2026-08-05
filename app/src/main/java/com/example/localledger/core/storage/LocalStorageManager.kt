package com.example.localledger.core.storage

import android.content.Context
import com.example.localledger.domain.error.LedgerException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StoragePaths(
    val rootDirectory: File,
    val dataDirectory: File,
    val configDirectory: File,
) {
    val databaseFile: File get() = File(dataDirectory, "ledger.db")
    val settingsFile: File get() = File(configDirectory, "settings.json")
}

@Singleton
class LocalStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureDirectories(): StoragePaths {
        try {
            val root = context.getExternalFilesDir(null)
                ?: throw LedgerException.LocalStorage()
            val data = File(root, "data")
            val config = File(root, "config")
            ensureDirectory(data)
            ensureDirectory(config)
            return StoragePaths(root, data, config)
        } catch (error: LedgerException.LocalStorage) {
            throw error
        } catch (error: Exception) {
            throw LedgerException.LocalStorage(error)
        }
    }

    private fun ensureDirectory(directory: File) {
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
            throw LedgerException.LocalStorage()
        }
    }
}

