package com.example.localledger.ui

import com.example.localledger.core.storage.AppSettings
import com.example.localledger.core.storage.BackendInitializer
import com.example.localledger.core.storage.InitializationState
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.repository.LedgerRepository
import com.example.localledger.widget.LedgerWidgetRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class SessionState(
    val initialization: InitializationState = InitializationState.Initializing,
    val settings: AppSettings? = null,
    val ledgers: List<Ledger> = emptyList(),
    val ledgerError: LedgerException.LocalStorage? = null,
) {
    val isReady: Boolean
        get() = initialization is InitializationState.Ready &&
            settings != null &&
            ledgerError == null
    val currentLedgerId: String? get() = settings?.currentLedgerId
    val currentLedgerName: String
        get() = ledgers.firstOrNull { it.id == settings?.currentLedgerId }?.name ?: ""
    val currentLedgerCurrency: String
        get() = ledgers.firstOrNull { it.id == settings?.currentLedgerId }?.currencyCode ?: "CNY"
    val themeMode: ThemeMode get() = settings?.themeMode ?: ThemeMode.SYSTEM
    val showDataLossNotice: Boolean
        get() = isReady && settings?.hasAcknowledgedDataLoss == false
}

@Singleton
class AppSession @Inject constructor(
    private val backendInitializer: BackendInitializer,
    private val settingsStore: SettingsStore,
    private val ledgerRepository: LedgerRepository,
    private val widgetRefresher: LedgerWidgetRefresher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val settingsFlow = MutableStateFlow<AppSettings?>(null)
    private val ledgersFlow = MutableStateFlow<List<Ledger>>(emptyList())
    private val ledgerErrorFlow = MutableStateFlow<LedgerException.LocalStorage?>(null)
    private val ledgerGeneration = AtomicInteger(0)
    private var ledgerJob: Job? = null

    val state: StateFlow<SessionState> = combine(
        backendInitializer.state,
        settingsFlow,
        ledgersFlow,
        ledgerErrorFlow,
    ) { initialization, settings, ledgers, ledgerError ->
        val effective = when (initialization) {
            is InitializationState.Ready -> settings ?: initialization.settings
            else -> settings
        }
        SessionState(initialization, effective, ledgers, ledgerError)
    }.stateIn(scope, SharingStarted.Eagerly, SessionState())

    init {
        collectLedgers()
    }

    suspend fun retryInitialization() {
        ledgerErrorFlow.value = null
        collectLedgers()
        backendInitializer.initialize()
    }

    suspend fun switchLedger(ledgerId: String) {
        settingsFlow.value = settingsStore.update { it.copy(currentLedgerId = ledgerId) }
        widgetRefresher.request()
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

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        return settingsStore.update(transform).also { settingsFlow.value = it }
    }

    suspend fun recordLastAccount(ledgerId: String, accountId: String) {
        settingsFlow.value = settingsStore.update { current ->
            current.copy(
                lastAccountIdByLedger = current.lastAccountIdByLedger + (ledgerId to accountId),
            )
        }
    }

    private fun collectLedgers() {
        val generation = ledgerGeneration.incrementAndGet()
        ledgerJob?.cancel()
        ledgerJob = scope.launch {
            ledgerRepository.observeLedgers()
                .catch { error ->
                    if (error is CancellationException) throw error
                    if (generation != ledgerGeneration.get()) return@catch
                    ledgerErrorFlow.value = error.asLocalStorage()
                }
                .collect { ledgers ->
                    if (generation != ledgerGeneration.get()) return@collect
                    ledgerErrorFlow.value = null
                    ledgersFlow.value = ledgers
                }
        }
    }
}

private fun Throwable.asLocalStorage(): LedgerException.LocalStorage = when (this) {
    is LedgerException.LocalStorage -> this
    else -> LedgerException.LocalStorage(this)
}
