package com.example.localledger.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.util.Locale

/**
 * Builds and parses the two wire formats. Streaming is intentionally not used:
 * tool calls and JSON drafts need a complete body, and the HTTP call already
 * runs off the main thread.
 */
object AiWire {
    private val json = Json { ignoreUnknownKeys = true }
    const val ANTHROPIC_VERSION = "2023-06-01"

    fun endpoint(format: AiWireFormat, baseUrl: String): String {
        val cleaned = normalizeBase(baseUrl)
        val suffix = when (format) {
            AiWireFormat.OPENAI_CHAT -> "/chat/completions"
            AiWireFormat.ANTHROPIC_MESSAGES -> "/v1/messages"
        }
        val without = when (format) {
            AiWireFormat.OPENAI_CHAT -> cleaned.removeSuffix("/chat/completions")
            AiWireFormat.ANTHROPIC_MESSAGES -> cleaned.removeSuffix("/v1/messages")
        }.trimEnd('/')
        return without + suffix
    }

    fun normalizeBase(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw AiCallException(AiFailure.BAD_URL)
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw AiCallException(AiFailure.BAD_URL)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") throw AiCallException(AiFailure.BAD_URL)
        if (!uri.rawUserInfo.isNullOrEmpty()) throw AiCallException(AiFailure.BAD_URL)
        if (uri.host.isNullOrBlank()) throw AiCallException(AiFailure.BAD_URL)
        return trimmed.trimEnd('/')
    }

    fun headers(format: AiWireFormat, apiKey: String): List<Pair<String, String>> = when (format) {
        AiWireFormat.OPENAI_CHAT -> listOf("Authorization" to "Bearer $apiKey")
        AiWireFormat.ANTHROPIC_MESSAGES -> listOf(
            "x-api-key" to apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
        )
    }

    fun body(format: AiWireFormat, model: String, request: AiChatRequest): String {
        val payload = when (format) {
            AiWireFormat.OPENAI_CHAT -> openAiBody(model, request)
            AiWireFormat.ANTHROPIC_MESSAGES -> anthropicBody(model, request)
        }
        return payload.toString()
    }

    fun parse(format: AiWireFormat, payload: String): AiChatResponse {
        val root = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (_: Exception) {
            throw AiCallException(AiFailure.UNAVAILABLE)
        }
        return when (format) {
            AiWireFormat.OPENAI_CHAT -> parseOpenAi(root)
            AiWireFormat.ANTHROPIC_MESSAGES -> parseAnthropic(root)
        }
    }

    private fun openAiBody(model: String, request: AiChatRequest): JsonObject = buildJsonObject {
        put("model", model)
        put("temperature", 0)
        put("messages", buildJsonArray {
            request.messages.forEach { add(openAiMessage(it)) }
        })
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                request.tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        })
                    })
                }
            })
        }
        if (request.jsonObject) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }

    private fun openAiMessage(message: AiMessage): JsonObject = buildJsonObject {
        when (message.role) {
            AiRole.SYSTEM -> {
                put("role", "system")
                put("content", message.text)
            }
            AiRole.USER -> {
                put("role", "user")
                put("content", message.text)
            }
            AiRole.TOOL -> {
                put("role", "tool")
                put("tool_call_id", message.toolCallId.orEmpty())
                put("content", message.text)
            }
            AiRole.ASSISTANT -> {
                put("role", "assistant")
                if (message.toolCalls.isNotEmpty() && message.text.isBlank()) {
                    put("content", JsonNull)
                } else {
                    put("content", message.text)
                }
                if (message.toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        message.toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.name)
                                    put("arguments", call.arguments.ifBlank { "{}" })
                                })
                            })
                        }
                    })
                }
            }
        }
    }

    private fun anthropicBody(model: String, request: AiChatRequest): JsonObject {
        val system = request.messages
            .filter { it.role == AiRole.SYSTEM }
            .joinToString("\n\n") { it.text }
            .trim()
        val conversational = ArrayList<JsonObject>()
        val pendingTools = ArrayList<AiMessage>()
        fun flushTools() {
            if (pendingTools.isEmpty()) return
            conversational += buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    pendingTools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", tool.toolCallId.orEmpty())
                            put("content", tool.text)
                        })
                    }
                })
            }
            pendingTools.clear()
        }
        for (message in request.messages) {
            when (message.role) {
                AiRole.SYSTEM -> Unit
                AiRole.TOOL -> pendingTools += message
                AiRole.USER -> {
                    flushTools()
                    conversational += buildJsonObject {
                        put("role", "user")
                        put("content", message.text)
                    }
                }
                AiRole.ASSISTANT -> {
                    flushTools()
                    conversational += buildJsonObject {
                        put("role", "assistant")
                        put("content", buildJsonArray {
                            if (message.text.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", message.text)
                                })
                            }
                            message.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", call.id)
                                    put("name", call.name)
                                    put("input", parseObject(call.arguments))
                                })
                            }
                            if (message.text.isBlank() && message.toolCalls.isEmpty()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", "")
                                })
                            }
                        })
                    }
                }
            }
        }
        flushTools()
        return buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens.coerceIn(1, 4096))
            put("temperature", 0)
            if (system.isNotEmpty()) put("system", system)
            put("messages", JsonArray(conversational))
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        })
                    }
                })
            }
        }
    }

    private fun parseOpenAi(root: JsonObject): AiChatResponse {
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiCallException(AiFailure.UNAVAILABLE)
        val message = choice["message"]?.jsonObject ?: throw AiCallException(AiFailure.UNAVAILABLE)
        val calls = message["tool_calls"]?.jsonArray.orEmpty().mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val function = obj["function"]?.jsonObject ?: obj
            val name = function.string("name") ?: return@mapIndexedNotNull null
            AiToolCall(
                id = obj.string("id") ?: "call_$index",
                name = name,
                arguments = argumentsText(function["arguments"]),
            )
        }.take(8)
        return AiChatResponse(
            message = AiMessage(
                role = AiRole.ASSISTANT,
                text = textFrom(message["content"]),
                toolCalls = calls,
            ),
            stopReason = choice.string("finish_reason"),
        )
    }

    private fun parseAnthropic(root: JsonObject): AiChatResponse {
        val content = root["content"]
        val text = textFrom(content)
        val calls = (content as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            if (obj.string("type") != "tool_use") return@mapIndexedNotNull null
            val name = obj.string("name") ?: return@mapIndexedNotNull null
            AiToolCall(
                id = obj.string("id") ?: "tool_$index",
                name = name,
                arguments = argumentsText(obj["input"]),
            )
        }.take(8)
        return AiChatResponse(
            message = AiMessage(role = AiRole.ASSISTANT, text = text, toolCalls = calls),
            stopReason = root.string("stop_reason"),
        )
    }

    private fun argumentsText(element: JsonElement?): String = when (element) {
        null, is JsonNull -> "{}"
        is JsonPrimitive -> element.contentOrNull?.ifBlank { "{}" } ?: "{}"
        is JsonObject -> element.toString()
        else -> "{}"
    }

    private fun textFrom(element: JsonElement?): String = when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.mapNotNull { block ->
            when (block) {
                is JsonPrimitive -> block.contentOrNull
                is JsonObject -> {
                    val type = block.string("type")
                    if (type != null && type != "text") null else block.string("text") ?: block.string("content")
                }
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
        else -> ""
    }

    private fun parseObject(text: String): JsonObject = try {
        json.parseToJsonElement(text.ifBlank { "{}" }) as? JsonObject ?: buildJsonObject { }
    } catch (_: Exception) {
        buildJsonObject { }
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { this[name] !is JsonNull }
}

internal fun aiBackoffMillis(attempt: Int): Long {
    val shift = (attempt - 1).coerceIn(0, 4)
    return 400L shl shift
}
