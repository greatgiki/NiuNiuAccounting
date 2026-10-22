package com.example.localledger.domain.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class QaTurn(val fromUser: Boolean, val text: String)

data class QaReply(
    val text: String,
    val citations: List<QueryCitation>,
)

@Singleton
class LedgerQa @Inject constructor(
    private val completer: AiCompleter,
    private val tools: LedgerToolExecutor,
) {
    suspend fun ask(
        ledgerId: String,
        currencyCode: String,
        history: List<QaTurn>,
        question: String,
    ): QaReply = withContext(Dispatchers.IO) {
        val citations = ArrayList<QueryCitation>()
        val messages = ArrayList<AiMessage>()
        messages += AiMessage(AiRole.SYSTEM, SYSTEM)
        history.takeLast(8).forEach { turn ->
            messages += AiMessage(
                role = if (turn.fromUser) AiRole.USER else AiRole.ASSISTANT,
                text = turn.text.take(500),
            )
        }
        messages += AiMessage(AiRole.USER, question.trim().take(300))
        val specs = LedgerTools.specs()
        repeat(MAX_ROUNDS) {
            val response = try {
                completer.complete(AiChatRequest(messages = messages.toList(), tools = specs))
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiCallException) {
                return@withContext QaReply(error.failure.message, emptyList())
            }
            val calls = response.message.toolCalls
            if (calls.isEmpty()) {
                if (citations.isEmpty()) return@withContext QaReply(NO_QUERY, emptyList())
                val text = LedgerTextRedactor.redact(response.message.text.trim())
                    .ifBlank { citations.joinToString("\n") { it.fact } }
                return@withContext QaReply(text, citations.toList())
            }
            messages += response.message
            calls.take(4).forEach { call ->
                val execution = tools.execute(ledgerId, currencyCode, call)
                execution.citation?.let { citations += it }
                messages += AiMessage(
                    role = AiRole.TOOL,
                    text = execution.modelContent,
                    toolCallId = call.id,
                    toolName = call.name,
                )
            }
        }
        val fallback = if (citations.isEmpty()) {
            NO_QUERY
        } else {
            citations.joinToString("\n") { "${it.periodLabel} · ${it.queryLabel}：${it.fact}" }
        }
        QaReply(fallback, citations.toList())
    }

    private companion object {
        const val MAX_ROUNDS = 3
        const val NO_QUERY = "这次没有查询账本，所以不能给出金额。可以问「这个月花了多少」或「预算还剩多少」。"
        const val SYSTEM = "你是这台手机上的记账助手。账本只在本机。" +
            "需要金额、笔数或预算时必须先调用工具，并且只引用工具返回的数字，不要估算或编造。" +
            "用简短的中文回答。不要索要或复述卡号、账号和账户名称。"
    }
}
