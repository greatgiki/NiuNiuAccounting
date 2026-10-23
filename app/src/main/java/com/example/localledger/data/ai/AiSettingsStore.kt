package com.example.localledger.data.ai

import android.util.Log
import com.example.localledger.core.storage.LocalStorageManager
import com.example.localledger.di.ApplicationScope
import com.example.localledger.domain.ai.AiPreset
import com.example.localledger.domain.ai.AiWireFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiAssistantSettings(
    val enabled: Boolean = false,
    val preset: AiPreset = AiPreset.OPENAI,
    val format: AiWireFormat = AiWireFormat.OPENAI_CHAT,
    val baseUrl: String = AiPreset.OPENAI.baseUrl,
    val model: String = AiPreset.OPENAI.model,
    /** 0 means the daily cap is off. */
    val dailyRequestCap: Int = 0,
)

@Singleton
class AiSettingsStore @Inject constructor(
    private val storageManager: LocalStorageManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(AiAssistantSettings())
    val settings: StateFlow<AiAssistantSettings> = state.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun current(): AiAssistantSettings = mutex.withLock {
        state.value.takeIf { loaded } ?: withContext(Dispatchers.IO) { read() }.also {
            loaded = true
            state.value = it
        }
    }

    suspend fun update(transform: (AiAssistantSettings) -> AiAssistantSettings): AiAssistantSettings = mutex.withLock {
        withContext(Dispatchers.IO) {
            val next = transform(if (loaded) state.value else read()).normalized()
            write(next)
            loaded = true
            state.value = next
            next
        }
    }

    private suspend fun reload() {
        mutex.withLock {
            val next = withContext(Dispatchers.IO) { read() }
            loaded = true
            state.value = next
        }
    }

    private fun read(): AiAssistantSettings {
        val file = file()
        if (!file.exists()) return AiAssistantSettings()
        return try {
            json.decodeFromString<AiAssistantSettings>(file.readText()).normalized()
        } catch (_: Exception) {
            Log.w("LocalLedger", "读取 AI 设置失败，已改用默认关闭")
            AiAssistantSettings()
        }
    }

    private fun write(settings: AiAssistantSettings) {
        val file = file()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(settings))
        if (file.exists() && !file.delete()) throw java.io.IOException("replace")
        if (!tmp.renameTo(file)) {
            file.writeText(json.encodeToString(settings))
            tmp.delete()
        }
    }

    private fun file(): File = File(storageManager.ensureDirectories().configDirectory, FILE_NAME)

    private var loaded = false

    private companion object {
        const val FILE_NAME = "ai-assistant.json"
    }
}

private fun AiAssistantSettings.normalized(): AiAssistantSettings = copy(
    baseUrl = baseUrl.trim(),
    model = model.trim(),
    dailyRequestCap = dailyRequestCap.coerceIn(0, 500),
)
