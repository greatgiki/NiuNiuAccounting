package com.example.localledger.domain.ai

import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.voice.VoiceAccountRef
import com.example.localledger.domain.voice.VoiceCategoryRef
import com.example.localledger.domain.voice.VoiceLedgerDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

data class RawAiItem(
    val amount: String?,
    val type: String?,
    val category: String?,
    val account: String?,
    val date: String?,
    val note: String?,
)

data class MappedDrafts(
    val drafts: List<VoiceLedgerDraft>,
    val dropped: Int,
)

object VoiceParseConfidence {
    /**
     * Confident when every item has a positive amount and a category that
     * already exists. Without a category, the type is only the parser's
     * default and is not treated as confident.
     */
    fun isConfident(drafts: List<VoiceLedgerDraft>): Boolean {
        if (drafts.isEmpty()) return false
        return drafts.all { draft ->
            val amount = draft.amount
            amount != null && amount.signum() > 0 &&
                !draft.categoryId.isNullOrBlank() &&
                draft.type != TransactionType.TRANSFER
        }
    }
}

object AiDrafts {
    private const val MAX_NAMES = 80

    fun request(
        utterance: String,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
    ): AiChatRequest {
        val expense = names(categories, CategoryType.EXPENSE)
        val income = names(categories, CategoryType.INCOME)
        val accountNames = accounts.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_NAMES)
        val spoken = utterance.trim().take(500)
        val user = buildString {
            append("今天：").append(today).append('\n')
            append("支出分类：").append(expense.joinToString("、").ifBlank { "（无）" }).append('\n')
            append("收入分类：").append(income.joinToString("、").ifBlank { "（无）" }).append('\n')
            append("账户：").append(accountNames.joinToString("、").ifBlank { "（无）" }).append('\n')
            append("原话：").append(spoken)
        }
        return AiChatRequest(
            messages = listOf(
                AiMessage(AiRole.SYSTEM, SYSTEM),
                AiMessage(AiRole.USER, user),
            ),
            jsonObject = true,
            maxTokens = 800,
        )
    }

    private fun names(categories: List<VoiceCategoryRef>, type: CategoryType): List<String> =
        categories.filter { it.type == type }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_NAMES)

    private const val SYSTEM = "你是记账草稿助手。只根据用户原话和给出的分类、账户名称返回一个 JSON 对象，不要解释。" +
        "格式：{\"items\":[{\"amount\":\"12.50\",\"type\":\"EXPENSE\",\"category\":\"餐饮\",\"account\":\"现金\",\"date\":\"2026-10-22\",\"note\":\"午饭\"}]}。" +
        "type 只能是 EXPENSE 或 INCOME。category 和 account 必须从给定名称里原样挑选，不确定就用空字符串。" +
        "date 用 YYYY-MM-DD，不确定就用今天。可以有多笔。不要编造金额，不要输出卡号或账号。"
}

object AiDraftJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<RawAiItem> {
        val cleaned = stripFence(text)
        if (cleaned.isBlank()) return emptyList()
        val element = try {
            json.parseToJsonElement(cleaned)
        } catch (_: Exception) {
            return emptyList()
        }
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> when (val items = element["items"]) {
                is JsonArray -> items
                else -> if (element.containsKey("amount")) JsonArray(listOf(element)) else return emptyList()
            }
            else -> return emptyList()
        }
        return array.mapNotNull { block ->
            val obj = block as? JsonObject ?: return@mapNotNull null
            RawAiItem(
                amount = field(obj, "amount"),
                type = field(obj, "type"),
                category = field(obj, "category"),
                account = field(obj, "account"),
                date = field(obj, "date"),
                note = field(obj, "note"),
            )
        }
    }

    private fun field(obj: JsonObject, name: String): String? {
        val value = obj[name] ?: return null
        val text = when (value) {
            is JsonPrimitive -> value.contentOrNull
            else -> null
        }?.trim()
        return text?.ifBlank { null }
    }

    internal fun stripFence(text: String): String {
        var trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        trimmed = trimmed.removePrefix("```")
        if (trimmed.startsWith("json", ignoreCase = true)) trimmed = trimmed.substring(4)
        if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length - 3)
        return trimmed.trim()
    }
}

object AiDraftValidator {
    private const val MAX_ITEMS = 20

    fun map(
        items: List<RawAiItem>,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
        sourceText: String,
    ): MappedDrafts {
        var dropped = 0
        val drafts = ArrayList<VoiceLedgerDraft>()
        for (item in items.take(MAX_ITEMS)) {
            val draft = mapOne(item, categories, accounts, today, sourceText)
            if (draft == null) dropped++ else drafts += draft
        }
        if (items.size > MAX_ITEMS) dropped += items.size - MAX_ITEMS
        return MappedDrafts(drafts, dropped)
    }

    private fun mapOne(
        item: RawAiItem,
        categories: List<VoiceCategoryRef>,
        accounts: List<VoiceAccountRef>,
        today: LocalDate,
        sourceText: String,
    ): VoiceLedgerDraft? {
        val type = parseType(item.type) ?: return null
        val amount = parseAmount(item.amount) ?: return null
        val category = matchCategory(item.category, type, categories)
        val account = matchAccount(item.account, accounts)
        val date = parseDate(item.date, today)
        val note = LedgerTextRedactor.redact(item.note.orEmpty()).take(200).trim()
        return VoiceLedgerDraft(
            type = type,
            amount = amount,
            categoryId = category?.id,
            categoryName = category?.name ?: item.category?.trim()?.takeIf { it.isNotEmpty() && !LedgerTextRedactor.looksLikeAccountSecret(it) },
            accountId = account?.id,
            accountName = account?.name,
            date = date,
            note = note,
            sourceText = sourceText.take(500),
        )
    }

    private fun parseType(raw: String?): TransactionType? = when (raw?.trim()?.uppercase(Locale.ROOT)) {
        "EXPENSE", "支出" -> TransactionType.EXPENSE
        "INCOME", "收入" -> TransactionType.INCOME
        else -> null
    }

    private fun parseAmount(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("，", "")
            .replace("￥", "")
            .replace("¥", "")
            .replace("元", "")
            .replace("块", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val minor = try {
            MoneyParser.toMinor(cleaned, minorDigits = 2)
        } catch (_: Exception) {
            return null
        }
        return BigDecimal(minor).movePointLeft(2)
    }

    private fun parseDate(raw: String?, today: LocalDate): LocalDate {
        if (raw.isNullOrBlank()) return today
        val parsed = try {
            LocalDate.parse(raw.trim().take(10))
        } catch (_: DateTimeParseException) {
            return today
        }
        if (parsed.isAfter(today.plusDays(1)) || parsed.isBefore(today.minusYears(10))) return today
        return parsed
    }

    private fun matchCategory(
        raw: String?,
        type: TransactionType,
        categories: List<VoiceCategoryRef>,
    ): VoiceCategoryRef? {
        val wanted = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return matchName(raw, categories.filter { it.type == wanted }) { it.name }
    }

    private fun matchAccount(raw: String?, accounts: List<VoiceAccountRef>): VoiceAccountRef? {
        if (raw != null && LedgerTextRedactor.looksLikeAccountSecret(raw)) return null
        return matchName(raw, accounts) { it.name }
    }

    private fun <T> matchName(raw: String?, options: List<T>, nameOf: (T) -> String): T? {
        val key = normalize(raw ?: return null)
        if (key.isEmpty() || LedgerTextRedactor.looksLikeAccountSecret(raw)) return null
        options.firstOrNull { normalize(nameOf(it)) == key }?.let { return it }
        val partial = options.filter { option ->
            val name = normalize(nameOf(option))
            name.length >= 2 && (key.contains(name) || name.contains(key))
        }
        return partial.singleOrNull()
    }

    private fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), "").lowercase(Locale.ROOT)
}
