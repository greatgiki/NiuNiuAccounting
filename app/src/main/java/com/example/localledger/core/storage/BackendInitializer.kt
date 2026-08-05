package com.example.localledger.core.storage

import androidx.room.withTransaction
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.data.local.database.PresetData
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InitializationState {
    data object Initializing : InitializationState
    data class Ready(val settings: AppSettings) : InitializationState
    data class Error(val error: LedgerException.LocalStorage) : InitializationState
}

@Singleton
class BackendInitializer @Inject constructor(
    private val databaseProvider: DatabaseProvider,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<InitializationState>(InitializationState.Initializing)
    val state: StateFlow<InitializationState> = mutableState.asStateFlow()

    fun start() {
        applicationScope.launch { initialize() }
    }

    suspend fun initialize(): InitializationState {
        mutableState.value = InitializationState.Initializing
        return try {
            val database = databaseProvider.get()
            val activeLedgerId = database.withTransaction {
                val ledgerDao = database.ledgerDao()
                if (ledgerDao.count() == 0) {
                    seedLedger(database, PresetData.DEFAULT_LEDGER_ID, "日常账本")
                }
                ledgerDao.findFirstActive()?.id ?: run {
                    val id = UUID.randomUUID().toString()
                    seedLedger(database, id, "日常账本")
                    id
                }
            }
            var settings = settingsStore.readOrCreate(activeLedgerId)
            val selected = database.ledgerDao().findById(settings.currentLedgerId)
            if (selected == null || selected.isArchived) {
                settings = settingsStore.update { it.copy(currentLedgerId = activeLedgerId) }
            }
            InitializationState.Ready(settings).also { mutableState.value = it }
        } catch (error: LedgerException.LocalStorage) {
            InitializationState.Error(error).also { mutableState.value = it }
        } catch (error: Exception) {
            InitializationState.Error(LedgerException.LocalStorage(error)).also {
                mutableState.value = it
            }
        }
    }

    private suspend fun seedLedger(
        database: com.example.localledger.data.local.database.WalletDatabase,
        ledgerId: String,
        name: String,
    ) {
        val now = clock.millis()
        database.ledgerDao().insert(PresetData.ledger(ledgerId, name, now))
        PresetData.accounts(ledgerId, now).forEach { database.accountDao().upsert(it) }
        PresetData.categories(ledgerId, now).forEach { database.categoryDao().upsert(it) }
    }
}

