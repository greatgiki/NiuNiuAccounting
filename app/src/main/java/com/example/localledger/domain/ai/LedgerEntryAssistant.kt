package com.example.localledger.domain.ai

import com.example.localledger.domain.voice.VoiceAccountRef
import com.example.localledger.domain.voice.VoiceCategoryRef
import com.example.localledger.domain.voice.VoiceLedgerDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class UtteranceInterpretation(
    val drafts: List<VoiceLedgerDraft>,
    val notice: String?,
)

@Singleton
class LedgerEntryAssistant @Inject constructor(
    private val completer: AiCompleter,
) {
    suspend fun interpret(
        utterance: String,
        local: List<VoiceLedgerDraft>,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
        force: Boolean,
        aiEnabled: Boolean,
    ): UtteranceInterpretation = withContext(Dispatchers.IO) {
        val confident = VoiceParseConfidence.isConfident(local)
        if (!force && confident) return@withContext UtteranceInterpretation(local, null)
        if (!aiEnabled) {
            val notice = if (force) AiFailure.DISABLED.message else null
            return@withContext UtteranceInterpretation(local, notice)
        }
        try {
            val response = completer.complete(AiDrafts.request(utterance, categories, accounts, today))
            val mapped = AiDraftValidator.map(
                items = AiDraftJson.parse(response.message.text),
                categories = categories,
                accounts = accounts,
                today = today,
                sourceText = utterance,
            )
            if (mapped.drafts.isEmpty()) {
                UtteranceInterpretation(local, "AI 没有给出可以核对的草稿，已保留本机解析。")
            } else {
                UtteranceInterpretation(mapped.drafts, "已用 AI 整理草稿，请确认后再记入。")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AiCallException) {
            val kept = if (local.isEmpty()) "" else "已保留本机解析。"
            UtteranceInterpretation(local, error.failure.message + kept)
        }
    }
}
