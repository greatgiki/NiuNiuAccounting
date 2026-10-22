package com.example.localledger.domain.ai

import com.example.localledger.data.ai.HttpAiProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiProviderHttpTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: LoopbackHttp? = null
    private val key = "sk-unit-test-key-9f3a"

    @After
    fun stop() {
        server?.stop()
    }

    @Test
    fun openAiBuildsToolsAndJsonModeAndParsesToolCall() = runBlocking {
        val captured = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val apiKeyHeader = AtomicReference<String>()
        server = listen("/chat/completions") { body, headers ->
            captured.set(body)
            authorization.set(headers["authorization"])
            apiKeyHeader.set(headers["x-api-key"])
            """
            {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"period_totals","arguments":"{\"period\":\"this_month\"}"}}
            ]}}]}
            """.trimIndent()
        }
        val response = provider().complete(endpoint(AiWireFormat.OPENAI_CHAT), sampleRequest())
        val root = json.parseToJsonElement(captured.get()).jsonObject
        assertEquals("gpt-test", root.string("model"))
        assertEquals("json_object", root["response_format"]!!.jsonObject.string("type"))
        assertEquals("period_totals", root["tools"]!!.jsonArray.first().jsonObject["function"]!!.jsonObject.string("name"))
        val messages = root["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject.string("role"))
        val assistant = messages[2].jsonObject
        assertEquals("assistant", assistant.string("role"))
        assertTrue(assistant["content"].toString() == "null")
        assertEquals("call_1", assistant["tool_calls"]!!.jsonArray.first().jsonObject.string("id"))
        val tool = messages[3].jsonObject
        assertEquals("tool", tool.string("role"))
        assertEquals("call_1", tool.string("tool_call_id"))
        assertFalse(captured.get().contains(key))
        assertEquals("Bearer $key", authorization.get())
        assertNull(apiKeyHeader.get())
        assertEquals("period_totals", response.message.toolCalls.single().name)
        assertEquals("this_month", json.parseToJsonElement(response.message.toolCalls.single().arguments).jsonObject.string("period"))
        assertEquals("tool_calls", response.stopReason)
    }

    @Test
    fun anthropicBuildsToolsAndParsesToolUse() = runBlocking {
        val captured = AtomicReference<String>()
        val apiKey = AtomicReference<String>()
        val version = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        server = listen("/v1/messages") { body, headers ->
            captured.set(body)
            apiKey.set(headers["x-api-key"])
            version.set(headers["anthropic-version"])
            authorization.set(headers["authorization"])
            """
            {"stop_reason":"tool_use","content":[
              {"type":"text","text":""},
              {"type":"tool_use","id":"toolu_1","name":"budget_status","input":{"month":"2026-10"}}
            ]}
            """.trimIndent()
        }
        val response = provider().complete(endpoint(AiWireFormat.ANTHROPIC_MESSAGES), sampleRequest())
        val root = json.parseToJsonElement(captured.get()).jsonObject
        assertTrue(root.string("system")!!.contains("只根据工具"))
        val tools = root["tools"]!!.jsonArray.first().jsonObject
        assertEquals("period_totals", tools.string("name"))
        assertEquals("object", tools["input_schema"]!!.jsonObject.string("type"))
        val messages = root["messages"]!!.jsonArray
        val toolResult = messages.last().jsonObject["content"]!!.jsonArray.first().jsonObject
        assertEquals("tool_result", toolResult.string("type"))
        assertEquals("call_1", toolResult.string("tool_use_id"))
        assertFalse(captured.get().contains(key))
        assertEquals(key, apiKey.get())
        assertEquals(AiWire.ANTHROPIC_VERSION, version.get())
        assertNull(authorization.get())
        val call = response.message.toolCalls.single()
        assertEquals("budget_status", call.name)
        assertEquals("2026-10", json.parseToJsonElement(call.arguments).jsonObject.string("month"))
    }

    @Test
    fun bothFormatsParseStructuredDraftJson() = runBlocking {
        val openAi = """{"choices":[{"finish_reason":"stop","message":{"content":"{\"items\":[{\"amount\":\"35\",\"type\":\"EXPENSE\",\"category\":\"餐饮\"}]}"}}]}"""
        val anthropic = """{"stop_reason":"end_turn","content":[{"type":"text","text":"```json\n{\"items\":[{\"amount\":\"20\",\"type\":\"INCOME\",\"category\":\"工资\"}]}\n```"}]}"""
        server = listen("/chat/completions") { _, _ -> openAi }
        val openAiText = provider().complete(
            endpoint(AiWireFormat.OPENAI_CHAT),
            AiChatRequest(listOf(AiMessage(AiRole.USER, "记一笔")), jsonObject = true),
        ).message.text
        server?.stop()
        server = listen("/v1/messages") { _, _ -> anthropic }
        val anthropicText = provider().complete(
            endpoint(AiWireFormat.ANTHROPIC_MESSAGES),
            AiChatRequest(listOf(AiMessage(AiRole.USER, "记一笔")), jsonObject = true),
        ).message.text
        assertEquals("35", AiDraftJson.parse(openAiText).single().amount)
        assertEquals("工资", AiDraftJson.parse(anthropicText).single().category)
    }

    @Test
    fun retriesAfter503ThenParses() = runBlocking {
        val hits = AtomicInteger()
        val delays = mutableListOf<Long>()
        server = listen("/chat/completions") { _, _ ->
            if (hits.incrementAndGet() == 1) {
                throw Status(503, """{"error":"busy $key"}""")
            }
            """{"choices":[{"message":{"content":"好"}}]}"""
        }
        val response = HttpAiProvider(sleeper = { delays += it }, maxAttempts = 3).complete(
            endpoint(AiWireFormat.OPENAI_CHAT),
            AiChatRequest(listOf(AiMessage(AiRole.USER, "ping"))),
        )
        assertEquals("好", response.message.text)
        assertEquals(listOf(400L), delays)
        assertEquals(2, hits.get())
    }

    @Test
    fun unauthorizedBodyDoesNotLeakTheKey() = runBlocking {
        server = listen("/chat/completions") { _, _ -> throw Status(401, """{"error":"bad $key"}""") }
        val error = runCatching {
            provider().complete(endpoint(AiWireFormat.OPENAI_CHAT), AiChatRequest(listOf(AiMessage(AiRole.USER, "ping"))))
        }.exceptionOrNull()
        assertTrue(error is AiCallException)
        assertEquals(AiFailure.UNAUTHORIZED, (error as AiCallException).failure)
        assertFalse(error.toString().contains(key))
        assertFalse(error.message.orEmpty().contains(key))
    }

    @Test
    fun endpointJoinsPresetsAndStripsARepeatedSuffix() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiWire.endpoint(AiWireFormat.OPENAI_CHAT, "https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiWire.endpoint(AiWireFormat.OPENAI_CHAT, "https://api.deepseek.com/"),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiWire.endpoint(AiWireFormat.ANTHROPIC_MESSAGES, "https://api.anthropic.com"),
        )
        assertEquals(
            "http://127.0.0.1:9/v1/messages",
            AiWire.endpoint(AiWireFormat.ANTHROPIC_MESSAGES, "http://127.0.0.1:9/v1/messages"),
        )
        assertEquals(AiFailure.BAD_URL, runCatching { AiWire.normalizeBase("http://user:secret@127.0.0.1") }.exceptionOrNull().let { (it as AiCallException).failure })
    }

    private fun provider() = HttpAiProvider(sleeper = {}, maxAttempts = 1)

    private fun endpoint(format: AiWireFormat): AiEndpoint {
        val port = server!!.port
        return AiEndpoint(format, "http://127.0.0.1:$port", "gpt-test", key)
    }

    private fun sampleRequest(): AiChatRequest = AiChatRequest(
        messages = listOf(
            AiMessage(AiRole.SYSTEM, "只根据工具回答"),
            AiMessage(AiRole.USER, "这个月花了多少"),
            AiMessage(
                role = AiRole.ASSISTANT,
                toolCalls = listOf(AiToolCall("call_1", "period_totals", """{"period":"this_month"}""")),
            ),
            AiMessage(role = AiRole.TOOL, text = """{"expense":"1.00"}""", toolCallId = "call_1", toolName = "period_totals"),
        ),
        tools = listOf(
            AiToolSpec("period_totals", "totals", buildJsonObject { put("type", "object") }),
        ),
        jsonObject = true,
    )

    private fun listen(
        path: String,
        handler: (String, Map<String, String>) -> String,
    ): LoopbackHttp = LoopbackHttp(path, handler)

    /**
     * Android unit tests compile against the SDK, which does not include
     * com.sun.net.httpserver. A loopback [ServerSocket] is enough.
     */
    private class LoopbackHttp(
        private val expectedPath: String,
        private val handler: (String, Map<String, String>) -> String,
    ) {
        private val socket = ServerSocket()
        val port: Int

        init {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            port = socket.localPort
            Thread({
                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        serve(client)
                    } catch (_: Exception) {
                        // The client already left, or the handler threw unexpectedly.
                    } finally {
                        try {
                            client.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }, "ai-test-http").apply { isDaemon = true }.start()
        }

        fun stop() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }

        private fun serve(client: Socket) {
            client.soTimeout = 10_000
            val request = readHttp(client.getInputStream())
            val (code, body) = if (request.path != expectedPath) {
                404 to "not found"
            } else {
                try {
                    200 to handler(request.body, request.headers)
                } catch (status: Status) {
                    status.code to status.body
                }
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $code ${reason(code)}\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            val output = client.getOutputStream()
            output.write(head.toByteArray(Charsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }

        private fun reason(code: Int) = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            503 -> "Service Unavailable"
            else -> "Error"
        }

        private fun readHttp(input: InputStream): RawHttp {
        val header = ByteArrayOutputStream()
        val window = ByteArray(4)
        var filled = 0
        while (header.size() < 64 * 1024) {
            val next = input.read()
            if (next < 0) break
            header.write(next)
            window[filled % 4] = next.toByte()
            filled++
            if (filled >= 4 &&
                window[(filled - 4) % 4] == '\r'.code.toByte() &&
                window[(filled - 3) % 4] == '\n'.code.toByte() &&
                window[(filled - 2) % 4] == '\r'.code.toByte() &&
                window[(filled - 1) % 4] == '\n'.code.toByte()
            ) {
                break
            }
        }
        val text = header.toString(Charsets.ISO_8859_1.name())
        val lines = text.split("\r\n")
        val path = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.substringBefore('?').orEmpty()
        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers.putIfAbsent(line.substring(0, colon).trim().lowercase(), line.substring(colon + 1).trim())
        }
        val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val bodyBytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bodyBytes, read, length - read)
            if (count < 0) break
            read += count
        }
            return RawHttp(path, headers, bodyBytes.toString(Charsets.UTF_8))
        }
    }

    private data class RawHttp(val path: String, val headers: Map<String, String>, val body: String)

    private class Status(val code: Int, val body: String) : RuntimeException()

    private fun kotlinx.serialization.json.JsonObject.string(name: String) = this[name]?.jsonPrimitive?.content
}
