package com.example.localledger.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.repository.LedgerRepository
import com.example.localledger.ui.AppSession
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

    fun create(name: String) {
        viewModelScope.launch {
            try {
                appSession.createLedgerAndSwitch(name)
            } catch (error: LedgerException) {
                effectChannel.send(LedgerEffect.ShowMessage(error.message ?: "创建账本失败"))
            }
        }
    }

    fun rename(ledger: Ledger, name: String) {
        viewModelScope.launch {
            try {
                ledgerRepository.updateLedger(ledger.copy(name = name))
            } catch (error: LedgerException) {
                effectChannel.send(LedgerEffect.ShowMessage(error.message ?: "修改账本失败"))
            }
        }
    }

    fun switchTo(ledgerId: String) {
        viewModelScope.launch {
            appSession.switchLedger(ledgerId)
        }
    }

    fun archive(ledger: Ledger) {
        val state = session.value
        val active = state.ledgers.filter { !it.isArchived }
        if (active.size <= 1) {
            viewModelScope.launch {
                effectChannel.send(LedgerEffect.ShowMessage("至少保留一个使用中的账本"))
            }
            return
        }
        viewModelScope.launch {
            try {
                if (state.currentLedgerId == ledger.id) {
                    val fallback = active.first { it.id != ledger.id }
                    appSession.switchLedger(fallback.id)
                }
                ledgerRepository.archiveLedger(ledger.id)
            } catch (error: LedgerException) {
                effectChannel.send(LedgerEffect.ShowMessage(error.message ?: "归档账本失败"))
            }
        }
    }
}
