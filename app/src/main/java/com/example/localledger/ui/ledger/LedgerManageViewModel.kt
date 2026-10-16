package com.example.localledger.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.repository.LedgerRepository
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.common.runOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LedgerEffect {
    data class ShowMessage(val message: String) : LedgerEffect
}

@HiltViewModel
class LedgerManageViewModel @Inject constructor(
    private val appSession: AppSession,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    val session: StateFlow<com.example.localledger.ui.SessionState> = appSession.state

    private val effectChannel = Channel<LedgerEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    suspend fun create(name: String, currencyCode: String): UiOutcome = runOutcome("创建账本失败") {
        val id = ledgerRepository.createLedger(name, currencyCode)
        appSession.switchLedger(id)
    }

    suspend fun rename(ledger: Ledger, name: String, currencyCode: String): UiOutcome =
        runOutcome("修改账本失败") {
            ledgerRepository.updateLedger(ledger.copy(name = name, currencyCode = currencyCode))
        }

    fun switchTo(ledgerId: String) {
        viewModelScope.launch {
            appSession.switchLedger(ledgerId)
        }
    }

    suspend fun archive(ledger: Ledger): UiOutcome {
        val state = session.value
        val active = state.ledgers.filter { !it.isArchived }
        if (active.size <= 1) {
            return UiOutcome.Failure("至少保留一个使用中的账本")
        }
        return runOutcome("归档账本失败") {
            if (state.currentLedgerId == ledger.id) {
                val fallback = active.first { it.id != ledger.id }
                appSession.switchLedger(fallback.id)
            }
            ledgerRepository.archiveLedger(ledger.id)
        }
    }
}
