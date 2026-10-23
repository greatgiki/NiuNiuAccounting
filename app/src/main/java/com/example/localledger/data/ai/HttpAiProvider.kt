package com.example.localledger.data.ai

import com.example.localledger.domain.ai.AiCallException
import com.example.localledger.domain.ai.AiChatRequest
import com.example.localledger.domain.ai.AiChatResponse
import com.example.localledger.domain.ai.AiEndpoint
import com.example.localledger.domain.ai.AiFailure
import com.example.localledger.domain.ai.AiProvider
import com.example.localledger.domain.ai.AiWire
import com.example.localledger.domain.ai.aiBackoffMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Non-streaming POST. A full body is required for tool calls and JSON drafts,
 * and the work stays on [Dispatchers.IO].
 */
class HttpAiProvider(
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val maxAttempts: Int = 3,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000,
) : AiProvider {

    override suspend fun complete(endpoint: AiEndpoint, request: AiChatRequest): AiChatResponse =
        withContext(Dispatchers.IO) {
            val url = AiWire.endpoint(endpoint.format, endpoint.baseUrl)
            val body = AiWire.body(endpoint.format, endpoint.model, request).toByteArray(Charsets.UTF_8)
            val headers = AiWire.headers(endpoint.format, endpoint.apiKey())
            var attempt = 1
            while (true) {
                ensureActive()
                try {
                    val result = post(url, headers, body)
                    if (result.code in 200..299) {
                        return@withContext try {
                            AiWire.parse(endpoint.format, result.body)
                        } catch (error: AiCallException) {
                            throw error
                        } catch (_: Exception) {
                            throw AiCallException(AiFailure.UNAVAILABLE)
                        }
                    }
                    if (result.code in RETRYABLE && attempt < maxAttempts) {
                        sleeper(aiBackoffMillis(attempt))
                        attempt++
                        continue
                    }
                    throw AiCallException(failureForStatus(result.code))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AiCallException) {
                    throw error
                } catch (error: IOException) {
                    if (!isActive) throw CancellationException("cancelled", error)
                    if (attempt >= maxAttempts) throw AiCallException(failureForIo(error), error)
                    sleeper(aiBackoffMillis(attempt))
                    attempt++
                }
            }
            error("unreachable")
        }

    private suspend fun post(url: String, headers: List<Pair<String, String>>, body: ByteArray): HttpResult =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "NiuNiuAccounting/1.0")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            coroutineContext.job.invokeOnCompletion { connection.disconnect() }
            try {
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResult(code, readLimited(stream))
            } finally {
                connection.disconnect()
            }
        }

    private fun readLimited(stream: InputStream?): String {
        if (stream == null) return ""
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                total += count
                if (total > MAX_BODY) throw IOException("response too large")
                buffer.write(chunk, 0, count)
            }
            return buffer.toString(Charsets.UTF_8.name())
        }
    }

    private fun failureForStatus(code: Int): AiFailure = when (code) {
        401, 403 -> AiFailure.UNAUTHORIZED
        429 -> AiFailure.RATE_LIMIT
        else -> AiFailure.UNAVAILABLE
    }

    private fun failureForIo(error: IOException): AiFailure = when (error) {
        is SocketTimeoutException -> AiFailure.TIMEOUT
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> AiFailure.NO_NETWORK
        else -> AiFailure.UNAVAILABLE
    }

    private data class HttpResult(val code: Int, val body: String)

    private companion object {
        const val MAX_BODY = 1_000_000
        val RETRYABLE = setOf(408, 429, 500, 502, 503, 504)
    }
}
