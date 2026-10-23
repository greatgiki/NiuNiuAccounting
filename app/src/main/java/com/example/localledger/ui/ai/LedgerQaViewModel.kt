package com.example.localledger.ui.ai

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.ai.AiSettingsStore
import com.example.localledger.domain.ai.AiFailure
import com.example.localledger.domain.ai.LedgerQa
import com.example.localledger.domain.ai.QaTurn
import com.example.localledger.domain.ai.QueryCitation
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Immutable
data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val citations: List<QueryCitation> = emptyList(),
    val pending: Boolean = false,
)

@HiltViewModel
class LedgerQaViewModel @Inject constructor(
    private val appSession: AppSession,
    private val settingsStore: AiSettingsStore,
    private val ledgerQa: LedgerQa,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settingsStore.settings
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.enabled)

    private val messagesState = kotlinx.coroutines.flow.MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = messagesState

    private val sendingState = kotlinx.coroutines.flow.MutableStateFlow(false)
    val sending: StateFlow<Boolean> = sendingState

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || sendingState.value) return
        if (!enabled.value) return
        val ledgerId = appSession.state.value.currentLedgerId
        if (ledgerId.isNullOrBlank()) {
            append(ChatMessage(UUID.randomUUID().toString(), fromUser = false, text = "请先选择账本"))
            return
        }
        val currency = appSession.state.value.currentLedgerCurrency
        val history = messagesState.value.filter { !it.pending }.map { QaTurn(it.fromUser, it.text) }
        val pendingId = UUID.randomUUID().toString()
        messagesState.value = messagesState.value + listOf(
            ChatMessage(UUID.randomUUID().toString(), fromUser = true, text = question.take(300)),
            ChatMessage(pendingId, fromUser = false, text = "正在查询账本…", pending = true),
        )
        sendingState.value = true
        viewModelScope.launch {
            try {
                val reply = ledgerQa.ask(ledgerId, currency, history, question)
                replacePending(pendingId, reply.text, reply.citations)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                replacePending(pendingId, AiFailure.UNAVAILABLE.message, emptyList())
            } finally {
                sendingState.value = false
            }
        }
    }

    private fun append(message: ChatMessage) {
        messagesState.value = messagesState.value + message
    }

    private fun replacePending(id: String, text: String, citations: List<QueryCitation>) {
        messagesState.value = messagesState.value.map { message ->
            if (message.id == id) message.copy(text = text, citations = citations, pending = false) else message
        }
    }
}
