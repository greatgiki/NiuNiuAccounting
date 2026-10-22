package com.example.localledger.domain.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class AiWireFormat {
    OPENAI_CHAT,
    ANTHROPIC_MESSAGES,
}

@Serializable
enum class AiPreset(
    val label: String,
    val format: AiWireFormat,
    val baseUrl: String,
    val model: String,
) {
    OPENAI("OpenAI", AiWireFormat.OPENAI_CHAT, "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic", AiWireFormat.ANTHROPIC_MESSAGES, "https://api.anthropic.com", "claude-haiku-4-5"),
    DEEPSEEK("DeepSeek", AiWireFormat.OPENAI_CHAT, "https://api.deepseek.com", "deepseek-chat"),
    CUSTOM("自定义", AiWireFormat.OPENAI_CHAT, "", ""),
}

enum class AiRole { SYSTEM, USER, ASSISTANT, TOOL }

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AiMessage(
    val role: AiRole,
    val text: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
)

data class AiToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class AiChatRequest(
    val messages: List<AiMessage>,
    val tools: List<AiToolSpec> = emptyList(),
    /** OpenAI `response_format: {type: json_object}`. Anthropic relies on the system prompt. */
    val jsonObject: Boolean = false,
    val maxTokens: Int = 1024,
)

data class AiChatResponse(
    val message: AiMessage,
    val stopReason: String? = null,
)

/**
 * [toString] leaves the key out so a log line cannot print it.
 */
class AiEndpoint(
    val format: AiWireFormat,
    val baseUrl: String,
    val model: String,
    private val apiKey: String,
) {
    fun apiKey(): String = apiKey

    override fun toString(): String = "AiEndpoint(format=$format, model=$model)"
}

enum class AiFailure(val message: String) {
    DISABLED("AI 助手还没开启。可以在设置里打开。"),
    NO_KEY("还没有 API 密钥。请在「AI 助手」里填写，密钥只保存在本机。"),
    KEY_UNREADABLE("无法读取已保存的密钥。请在「AI 助手」里重新填写。"),
    NO_MODEL("请先填写模型名称。"),
    BAD_URL("接口地址不正确。请使用 http 或 https，不要把密钥写进地址。"),
    LOCKED("请先解锁应用。"),
    NO_NETWORK("没有连上网络。"),
    TIMEOUT("连接超时。请稍后再试。"),
    UNAUTHORIZED("密钥或地址没有通过校验。请检查设置里的密钥和接口地址。"),
    RATE_LIMIT("对方暂时限制了请求。请稍后再试。"),
    CAP("今天的请求已达上限。可以明天再试，或在设置里把上限调高。填 0 表示不限制。"),
    UNAVAILABLE("AI 服务暂时不可用。请稍后再试。"),
}

class AiCallException(
    val failure: AiFailure,
    cause: Throwable? = null,
) : Exception(failure.message, cause)

fun interface AiCompleter {
    suspend fun complete(request: AiChatRequest): AiChatResponse
}

object AiAccess {
    fun check(
        locked: Boolean,
        enabled: Boolean,
        requireEnabled: Boolean,
        apiKey: String?,
        keyUnreadable: Boolean,
        model: String,
        baseUrl: String,
        cap: Int,
        used: Int,
    ): AiFailure? = when {
        locked -> AiFailure.LOCKED
        requireEnabled && !enabled -> AiFailure.DISABLED
        keyUnreadable && apiKey.isNullOrBlank() -> AiFailure.KEY_UNREADABLE
        apiKey.isNullOrBlank() -> AiFailure.NO_KEY
        model.isBlank() -> AiFailure.NO_MODEL
        baseUrl.isBlank() -> AiFailure.BAD_URL
        cap > 0 && used >= cap -> AiFailure.CAP
        else -> null
    }
}
