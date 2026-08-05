package com.example.localledger.ui

import com.example.localledger.core.storage.AppSettings
import com.example.localledger.core.storage.BackendInitializer
import com.example.localledger.core.storage.InitializationState
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class SessionState(
    val initialization: InitializationState = InitializationState.Initializing,
    val settings: AppSettings? = null,
    val ledgers: List<Ledger> = emptyList(),
) {
    val isReady: Boolean get() = initialization is InitializationState.Ready && settings != null
    val currentLedgerId: String? get() = settings?.currentLedgerId
    val currentLedgerName: String
        get() = ledgers.firstOrNull { it.id == settings?.currentLedgerId }?.name ?: ""
    val themeMode: ThemeMode get() = settings?.themeMode ?: ThemeMode.SYSTEM
    val showDataLossNotice: Boolean
        get() = isReady && settings?.hasAcknowledgedDataLoss == false
}

@Singleton
class AppSession @Inject constructor(
    private val backendInitializer: BackendInitializer,
    private val settingsStore: SettingsStore,
    private val ledgerRepository: LedgerRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val settingsFlow = MutableStateFlow<AppSettings?>(null)

    val state: StateFlow<SessionState> = combine(
        backendInitializer.state,
        settingsFlow,
        ledgerRepository.observeLedgers(),
    ) { initialization, settings, ledgers ->
        val effective = when (initialization) {
            is InitializationState.Ready -> settings ?: initialization.settings
            else -> settings
        }
        SessionState(initialization, effective, ledgers)
    }.stateIn(scope, SharingStarted.Eagerly, SessionState())

    suspend fun retryInitialization() {
        backendInitializer.initialize()
    }

    suspend fun switchLedger(ledgerId: String) {
        settingsFlow.value = settingsStore.update { it.copy(currentLedgerId = ledgerId) }
    }

    suspend fun createLedgerAndSwitch(name: String) {
        val id = ledgerRepository.createLedger(name)
        switchLedger(id)
    }

    suspend fun setTheme(themeMode: ThemeMode) {
        settingsFlow.value = settingsStore.update { it.copy(themeMode = themeMode) }
    }

    suspend fun acknowledgeDataLoss() {
        settingsFlow.value = settingsStore.update { it.copy(hasAcknowledgedDataLoss = true) }
    }

    suspend fun recordLastAccount(ledgerId: String, accountId: String) {
        settingsFlow.value = settingsStore.update { current ->
            current.copy(
                lastAccountIdByLedger = current.lastAccountIdByLedger + (ledgerId to accountId),
            )
        }
    }
}
