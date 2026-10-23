package com.example.localledger.data.ai

import android.content.Context
import com.example.localledger.domain.time.ZoneIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiUsageRecord(
    val day: String = "",
    val count: Int = 0,
)

@Singleton
class AiUsageStore @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
    private val zones: ZoneIdProvider,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun usedToday(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val record = read()
            if (record.day == today()) record.count.coerceAtLeast(0) else 0
        }
    }

    suspend fun increment() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val today = today()
            val record = read()
            val next = if (record.day == today) {
                record.copy(count = (record.count + 1).coerceAtMost(10_000))
            } else {
                AiUsageRecord(today, 1)
            }
            write(next)
        }
    }

    private fun today(): String = LocalDate.now(clock.withZone(zones.current())).toString()

    private fun read(): AiUsageRecord {
        if (!file.exists()) return AiUsageRecord()
        return try {
            json.decodeFromString(file.readText())
        } catch (_: Exception) {
            AiUsageRecord()
        }
    }

    private fun write(record: AiUsageRecord) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(record))
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(json.encodeToString(record))
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "ai-usage.json"
    }
}
