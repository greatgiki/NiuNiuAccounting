package com.example.localledger.notification

import android.content.Context
import com.example.localledger.data.repository.CapturedNotification
import com.example.localledger.data.repository.NotificationRecordResult
import com.example.localledger.data.repository.describe
import com.example.localledger.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NotificationDiagnostic(
    val id: String,
    val receivedAt: Long,
    val packageName: String,
    val postedAt: Long,
    val excerpt: String,
    val outcome: String,
    val patternId: String? = null,
)

@Singleton
class NotificationDiagnosticsStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "notification-diagnostics.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val entries = MutableStateFlow<List<NotificationDiagnostic>>(emptyList())
    private var loaded = false

    val recent: StateFlow<List<NotificationDiagnostic>> = entries.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { ensureLoaded() }
        }
    }

    suspend fun record(event: CapturedNotification, result: NotificationRecordResult) {
        val patternId = when (result) {
            is NotificationRecordResult.Created -> result.patternId
            is NotificationRecordResult.Enriched -> result.patternId
            else -> null
        }
        val summary = if (event.isGroupSummary) "分组摘要 · " else ""
        val entry = NotificationDiagnostic(
            id = UUID.randomUUID().toString(),
            receivedAt = System.currentTimeMillis(),
            packageName = event.packageName,
            postedAt = event.postedAt,
            excerpt = event.text.replace('\n', ' ').take(180),
            outcome = summary + result.describe(),
            patternId = patternId,
        )
        mutex.withLock {
            ensureLoaded()
            val next = (listOf(entry) + entries.value).take(MAX_ENTRIES)
            entries.value = next
            withContext(Dispatchers.IO) { write(next) }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        entries.value = read()
        loaded = true
    }

    private fun read(): List<NotificationDiagnostic> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<NotificationDiagnostic>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(values: List<NotificationDiagnostic>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(values))
    }

    companion object {
        const val MAX_ENTRIES = 20
    }
}
