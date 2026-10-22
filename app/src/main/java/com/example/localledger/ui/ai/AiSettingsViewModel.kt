package com.example.localledger.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.ai.AiCredentialStore
import com.example.localledger.data.ai.AiSettingsStore
import com.example.localledger.data.ai.CredentialRead
import com.example.localledger.domain.ai.AiCallException
import com.example.localledger.domain.ai.AiChatRequest
import com.example.localledger.domain.ai.AiFailure
import com.example.localledger.domain.ai.AiMessage
import com.example.localledger.domain.ai.AiPreset
import com.example.localledger.domain.ai.AiRole
import com.example.localledger.domain.ai.AiWireFormat
import com.example.localledger.data.ai.AiGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class KeyStatus { Unknown, Absent, Saved, Unreadable }

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsStore: AiSettingsStore,
    private val credentials: AiCredentialStore,
    private val gateway: AiGateway,
) : ViewModel() {
    val settings = settingsStore.settings
    private val keyState = MutableStateFlow(KeyStatus.Unknown)
    val keyStatus: StateFlow<KeyStatus> = keyState.asStateFlow()
    private val messages = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = messages.asStateFlow()
    private val testing = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = testing.asStateFlow()

    init {
        viewModelScope.launch { refreshKey() }
    }

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun applyPreset(preset: AiPreset) = edit { current ->
        if (preset == AiPreset.CUSTOM) {
            current.copy(preset = preset)
        } else {
            current.copy(
                preset = preset,
                format = preset.format,
                baseUrl = preset.baseUrl,
                model = preset.model,
            )
        }
    }

    fun setFormat(format: AiWireFormat) = edit { current ->
        val preset = if (current.preset != AiPreset.CUSTOM && current.preset.format != format) {
            AiPreset.CUSTOM
        } else {
            current.preset
        }
        current.copy(format = format, preset = preset)
    }

    fun setBaseUrl(value: String) = edit { it.copy(baseUrl = value.take(300)) }

    fun setModel(value: String) = edit { it.copy(model = value.take(120)) }

    fun setDailyCap(value: Int) = edit { it.copy(dailyRequestCap = value.coerceIn(0, 500)) }

    fun saveKey(key: String) {
        viewModelScope.launch {
            credentials.write(key)
            refreshKey()
            messages.value = if (key.isBlank()) "已清除本机密钥" else "密钥已加密保存在本机"
        }
    }

    fun clearKey() = saveKey("")

    fun testConnection(typedKey: String) {
        if (testing.value) return
        viewModelScope.launch {
            testing.value = true
            messages.value = null
            try {
                gateway.test(pingRequest(), typedKey.ifBlank { null })
                messages.value = "连接成功"
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiCallException) {
                messages.value = error.failure.message
            } catch (_: Exception) {
                messages.value = AiFailure.UNAVAILABLE.message
            } finally {
                testing.value = false
            }
        }
    }

    private fun edit(transform: (com.example.localledger.data.ai.AiAssistantSettings) -> com.example.localledger.data.ai.AiAssistantSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    private suspend fun refreshKey() {
        keyState.value = when (credentials.read()) {
            CredentialRead.Absent -> KeyStatus.Absent
            is CredentialRead.Present -> KeyStatus.Saved
            CredentialRead.Unreadable -> KeyStatus.Unreadable
        }
    }

    private fun pingRequest() = AiChatRequest(
        messages = listOf(
            AiMessage(AiRole.SYSTEM, "只回复一个字：好"),
            AiMessage(AiRole.USER, "ping"),
        ),
        maxTokens = 16,
    )
}
