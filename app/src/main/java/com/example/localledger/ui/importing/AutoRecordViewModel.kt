package com.example.localledger.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.repository.describe
import com.example.localledger.notification.NotificationCapturePipeline
import com.example.localledger.notification.NotificationDiagnostic
import com.example.localledger.notification.NotificationDiagnosticsStore
import com.example.localledger.notification.NotificationListenerHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoRecordViewModel @Inject constructor(
    diagnostics: NotificationDiagnosticsStore,
    private val pipeline: NotificationCapturePipeline,
) : ViewModel() {
    val connected: StateFlow<Boolean> = NotificationListenerHealth.connected
    val recent: StateFlow<List<NotificationDiagnostic>> = diagnostics.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val lastTest = MutableStateFlow<String?>(null)
    val testMessage: StateFlow<String?> = lastTest.asStateFlow()

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
