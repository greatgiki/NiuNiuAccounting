package com.example.localledger.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.capture.CaptureCoordinator
import com.example.localledger.data.repository.describe
import com.example.localledger.domain.capture.NamedChoice
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.domain.repository.CategoryRepository
import com.example.localledger.domain.repository.LedgerRepository
import com.example.localledger.notification.NotificationCapturePipeline
import com.example.localledger.notification.NotificationDiagnostic
import com.example.localledger.notification.NotificationDiagnosticsStore
import com.example.localledger.notification.NotificationListenerHealth
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureCatalog(
    val ledgerId: String = "",
    val ledgers: List<NamedChoice> = emptyList(),
    val accounts: List<NamedChoice> = emptyList(),
    val expenseCategories: List<NamedChoice> = emptyList(),
    val incomeCategories: List<NamedChoice> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoRecordViewModel @Inject constructor(
    appSession: AppSession,
    private val diagnostics: NotificationDiagnosticsStore,
    private val pipeline: NotificationCapturePipeline,
    private val coordinator: CaptureCoordinator,
    ledgerRepository: LedgerRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    val connected: StateFlow<Boolean> = NotificationListenerHealth.connected
    val recent: StateFlow<List<NotificationDiagnostic>> = diagnostics.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val lastTest = MutableStateFlow<String?>(null)
    val testMessage: StateFlow<String?> = lastTest.asStateFlow()

    val catalog: StateFlow<CaptureCatalog> = appSession.state
        .flatMapLatest { state ->
            state.settings?.let { coordinator.apply(it) }
            val ledgerId = state.settings?.captureLedgerId?.takeIf { it.isNotBlank() } ?: state.currentLedgerId
            if (ledgerId.isNullOrBlank()) {
                flowOf(CaptureCatalog())
            } else {
                combine(
                    ledgerRepository.observeLedgers(),
                    accountRepository.observeAccounts(ledgerId),
                    categoryRepository.observeCategories(ledgerId, CategoryType.EXPENSE),
                    categoryRepository.observeCategories(ledgerId, CategoryType.INCOME),
                ) { ledgers, accounts, expenses, incomes ->
                    CaptureCatalog(
                        ledgerId = ledgerId,
                        ledgers = ledgers.filter { !it.isArchived }.map { NamedChoice(it.id, it.name) },
                        accounts = accounts.filter { !it.isArchived }.map { NamedChoice(it.id, it.name) },
                        expenseCategories = expenses.filter { !it.isArchived }.map { NamedChoice(it.id, it.name) },
                        incomeCategories = incomes.filter { !it.isArchived }.map { NamedChoice(it.id, it.name) },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureCatalog())

    fun clearDiagnostics() {
        viewModelScope.launch { diagnostics.clear() }
    }

    fun simulate() {
        viewModelScope.launch {
            val result = runCatching { pipeline.simulateSample() }
            lastTest.value = result.fold(
                onSuccess = { it.describe() },
                onFailure = { "测试失败：${it.message ?: "未知错误"}" },
            )
        }
    }
}
