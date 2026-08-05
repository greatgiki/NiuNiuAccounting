package com.example.localledger.data.local.database

import android.content.Context
import androidx.room.Room
import com.example.localledger.core.storage.LocalStorageManager
import com.example.localledger.domain.error.LedgerException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: LocalStorageManager,
) {
    @Volatile private var instance: WalletDatabase? = null
    private val mutex = Mutex()

    suspend fun get(): WalletDatabase = instance ?: mutex.withLock {
        instance ?: createDatabase().also { instance = it }
    }

    private suspend fun createDatabase(): WalletDatabase = withContext(Dispatchers.IO) {
        try {
            val databaseFile = storageManager.ensureDirectories().databaseFile
            Room.databaseBuilder(
                context,
                WalletDatabase::class.java,
                databaseFile.absolutePath,
            )
                .addMigrations(*WalletDatabase.MIGRATIONS)
                .addCallback(WalletDatabase.INTEGRITY_CALLBACK)
                .build()
        } catch (error: LedgerException.LocalStorage) {
            throw error
        } catch (error: Exception) {
            throw LedgerException.LocalStorage(error)
        }
    }
}

