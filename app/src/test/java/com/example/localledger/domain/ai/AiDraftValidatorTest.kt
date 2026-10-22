package com.example.localledger.domain.ai

import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.voice.VoiceAccountRef
import com.example.localledger.domain.voice.VoiceCategoryRef
import com.example.localledger.domain.voice.VoiceLedgerDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AiDraftValidatorTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val categories = listOf(
        VoiceCategoryRef("food", "餐饮", CategoryType.EXPENSE),
        VoiceCategoryRef("takeout", "餐饮外卖", CategoryType.EXPENSE),
        VoiceCategoryRef("salary", "工资", CategoryType.INCOME),
    )
    private val accounts = listOf(
        VoiceAccountRef("cash", "现金", AccountType.CASH),
        VoiceAccountRef("wx", "微信", AccountType.E_WALLET),
    )

    @Test
    fun mapsExactNamesAndKeepsSeveralItems() {
        val mapped = AiDraftValidator.map(
            listOf(
                RawAiItem("35.50", "EXPENSE", "餐饮", "现金", "2026-10-21", "午饭"),
                RawAiItem("8", "支出", " 微信 ", "微信", null, "地铁"),
            ),
            categories,
            accounts,
            today,
            "午饭和地铁",
        )
        assertEquals(0, mapped.dropped)
        assertEquals(2, mapped.drafts.size)
        assertEquals(BigDecimal("35.50"), mapped.drafts[0].amount)
        assertEquals("food", mapped.drafts[0].categoryId)
        assertEquals("cash", mapped.drafts[0].accountId)
        assertEquals(LocalDate.of(2026, 10, 21), mapped.drafts[0].date)
        assertNull(mapped.drafts[1].categoryId)
        assertEquals("wx", mapped.drafts[1].accountId)
        assertEquals(today, mapped.drafts[1].date)
    }

    @Test
    fun dropsInvalidAmountsAndTypesAndLeavesAmbiguousCategoriesUnmapped() {
        val mapped = AiDraftValidator.map(
            listOf(
                RawAiItem("0", "EXPENSE", "餐饮", "现金", null, null),
                RawAiItem("-3", "INCOME", "工资", "现金", null, null),
                RawAiItem("12", "TRANSFER", "餐饮", "现金", null, null),
                RawAiItem("不是数字", "EXPENSE", "餐饮", "现金", null, null),
                RawAiItem("9", "INCOME", "餐饮", "现金", null, null),
                RawAiItem("6", "EXPENSE", "点了餐饮外卖", "现金", "2099-01-01", "尾号6222021234567890"),
            ),
            categories,
            accounts,
            today,
            "原话",
        )
        assertEquals(2, mapped.drafts.size)
        val kept = mapped.drafts.last()
        assertNull(kept.categoryId)
        assertEquals("点了餐饮外卖", kept.categoryName)
        assertEquals(today, kept.date)
        assertFalse(kept.note.contains("6222"))
        assertTrue(kept.note.contains("****"))
        assertEquals(4, mapped.dropped)
    }

    @Test
    fun dropsCardLikeAccountAndDoesNotInventACategory() {
        val mapped = AiDraftValidator.map(
            listOf(RawAiItem("10", "EXPENSE", "没有这种分类", "6222021234567890123", null, null)),
            categories,
            accounts,
            today,
            "原话",
        )
        val draft = mapped.drafts.single()
        assertNull(draft.categoryId)
        assertNull(draft.accountId)
        assertEquals("没有这种分类", draft.categoryName)
    }

    @Test
    fun confidenceRequiresAmountAndCategory() {
        val ready = VoiceLedgerDraft(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("10"),
            categoryId = "food",
            categoryName = "餐饮",
            accountId = "cash",
            accountName = "现金",
            date = today,
            note = "",
            sourceText = "午饭10",
        )
        assertTrue(VoiceParseConfidence.isConfident(listOf(ready)))
        assertFalse(VoiceParseConfidence.isConfident(emptyList()))
        assertFalse(VoiceParseConfidence.isConfident(listOf(ready.copy(amount = null))))
        assertFalse(VoiceParseConfidence.isConfident(listOf(ready.copy(categoryId = null))))
    }

    @Test
    fun promptCarriesOnlyTheUtteranceAndNames() {
        val request = AiDrafts.request("午饭三十五", categories, accounts, today)
        val text = request.messages.joinToString("\n") { it.text }
        assertTrue(text.contains("午饭三十五"))
        assertTrue(text.contains("餐饮"))
        assertTrue(text.contains("现金"))
        assertFalse(text.contains("BALANCE"))
        assertFalse(text.contains("6222"))
        assertTrue(request.jsonObject)
    }

    @Test
    fun assistantSkipsTheModelWhenTheLocalParseIsConfident() = runBlocking {
        var calls = 0
        val assistant = LedgerEntryAssistant { calls++; error("should not call") }
        val local = listOf(confidentDraft())
        val result = assistant.interpret("午饭10", local, categories, accounts, today, force = false, aiEnabled = true)
        assertEquals(0, calls)
        assertEquals(local, result.drafts)
        assertNull(result.notice)
    }

    @Test
    fun assistantMapsTheModelReplyAndKeepsLocalDraftsWhenTheCallFails() = runBlocking {
        val assistant = LedgerEntryAssistant { request ->
            val text = request.messages.joinToString { it.text }
            assertTrue(text.contains("地铁"))
            assertTrue(text.contains("餐饮"))
            assertFalse(text.contains("6222"))
            AiChatResponse(
                AiMessage(
                    AiRole.ASSISTANT,
                    """{"items":[{"amount":"20","type":"EXPENSE","category":"餐饮","account":"微信","date":"2026-10-22","note":"地铁"}]}""",
                ),
            )
        }
        val local = listOf(confidentDraft().copy(amount = null, categoryId = null))
        val result = assistant.interpret("地铁", local, categories, accounts, today, force = false, aiEnabled = true)
        assertEquals("food", result.drafts.single().categoryId)
        assertEquals("wx", result.drafts.single().accountId)
        assertEquals(BigDecimal("20.00"), result.drafts.single().amount)

        val failing = LedgerEntryAssistant { throw AiCallException(AiFailure.NO_NETWORK) }
        val fallback = failing.interpret("地铁", local, categories, accounts, today, force = true, aiEnabled = true)
        assertEquals(local, fallback.drafts)
        assertTrue(fallback.notice!!.contains("没有连上网络"))
        assertTrue(fallback.notice.contains("本机解析"))

        val disabled = LedgerEntryAssistant { error("no") }
        val skipped = disabled.interpret("地铁", local, categories, accounts, today, force = true, aiEnabled = false)
        assertEquals(AiFailure.DISABLED.message, skipped.notice)
    }

    private fun confidentDraft() = VoiceLedgerDraft(
        type = TransactionType.EXPENSE,
        amount = BigDecimal.TEN,
        categoryId = "food",
        categoryName = "餐饮",
        accountId = "cash",
        accountName = "现金",
        date = today,
        note = "",
        sourceText = "午饭10",
    )
}
