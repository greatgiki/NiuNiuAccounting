package com.example.localledger.domain.ai

import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Transaction
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.data.ai.AiCredentialStore
import com.example.localledger.data.ai.CredentialRead
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.security.SecretBox
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class LedgerToolExecutorTest {
    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), zone)
    private val ranges = DateRangeResolver { zone }
    private val source = FakeSource()
    private val executor = LedgerToolExecutor(source, ranges, clock)

    @Test
    fun totalsAndComparisonUseTheLocalNumbers() = runBlocking {
        val totals = executor.execute("ledger", "CNY", AiToolCall("1", LedgerToolNames.TOTALS, """{"period":"this_month"}"""))
        assertTrue(totals.modelContent.contains("\"expense\":\"34.50\""))
        assertEquals("收支合计", totals.citation?.queryLabel)
        assertTrue(totals.citation!!.periodLabel.contains("2026年10月"))
        assertTrue(totals.citation.fact.contains("¥34.50"))
        assertFalse(totals.modelContent.contains("招商银行"))

        val compare = executor.execute(
            "ledger",
            "CNY",
            AiToolCall("2", LedgerToolNames.COMPARE, """{"first":"this_month","second":"last_month"}"""),
        )
        assertEquals("期间对比", compare.citation?.queryLabel)
        assertTrue(compare.citation!!.periodLabel.contains("上月"))
        assertEquals(3, source.summaryCalls)
    }

    @Test
    fun categoryBreakdownIsCappedAndTopTransactionsAreRedacted() = runBlocking {
        val categories = executor.execute(
            "ledger",
            "CNY",
            AiToolCall("1", LedgerToolNames.CATEGORIES, """{"period":"this_month","direction":"EXPENSE","limit":3}"""),
        )
        val items = jsonObject(categories.modelContent)["items"]!!.jsonArray
        assertEquals(3, items.size)
        assertEquals("分类15", items.first().jsonObject.string("category"))
        assertTrue(jsonObject(categories.modelContent)["truncated"]!!.jsonPrimitive.content == "true")
        assertTrue(categories.citation!!.fact.contains("其余合计"))

        val top = executor.execute(
            "ledger",
            "CNY",
            AiToolCall("2", LedgerToolNames.TOP, """{"period":"this_month","limit":3}"""),
        )
        val body = top.modelContent + top.citation!!.fact
        assertFalse(body.contains("招商银行"))
        assertFalse(body.contains("6222021234567890123"))
        assertFalse(body.contains("6222021234567890"))
        assertTrue(body.contains("****"))
        assertEquals(3, jsonObject(top.modelContent)["items"]!!.jsonArray.size)
        assertEquals("90.00", jsonObject(top.modelContent)["items"]!!.jsonArray.first().jsonObject.string("amount"))
        assertFalse(body.contains("account"))
    }

    @Test
    fun budgetStatusOmitsAccountsAndUnknownToolsFailSoftly() = runBlocking {
        val budget = executor.execute("ledger", "CNY", AiToolCall("1", LedgerToolNames.BUDGET, """{"month":"2026-10"}"""))
        assertTrue(budget.citation!!.fact.contains("总预算"))
        assertTrue(budget.citation.fact.contains("¥34.50"))
        assertTrue(budget.citation.fact.contains("¥100.00"))
        assertFalse(budget.modelContent.contains("招商银行"))
        val unknown = executor.execute("ledger", "CNY", AiToolCall("2", "delete_all", "{}"))
        assertNull(unknown.citation)
        assertTrue(unknown.modelContent.contains("没有这个查询"))
    }

    @Test
    fun redactionHidesCardsAndStaysStable() {
        val masked = LedgerTextRedactor.redact("尾号 6222 0212 3456 7890 午饭 35.50")
        assertFalse(masked.contains("6222"))
        assertTrue(masked.contains("35.50"))
        assertTrue(masked.contains("午饭"))
        assertEquals(masked, LedgerTextRedactor.redact(masked))
        assertEquals("尾号****", LedgerTextRedactor.redact("尾号1234"))
    }

    @Test
    fun answersWithoutAToolCallDoNotKeepInventedNumbers() = runBlocking {
        val invented = LedgerQa(AiCompleter { AiChatResponse(AiMessage(AiRole.ASSISTANT, "花了9999元")) }, executor)
        val blocked = invented.ask("ledger", "CNY", emptyList(), "这个月花了多少")
        assertFalse(blocked.text.contains("9999"))
        assertTrue(blocked.citations.isEmpty())

        var round = 0
        val qa = LedgerQa(
            AiCompleter {
                round++
                if (round == 1) {
                    AiChatResponse(
                        AiMessage(
                            AiRole.ASSISTANT,
                            toolCalls = listOf(AiToolCall("c1", LedgerToolNames.TOTALS, """{"period":"this_month"}""")),
                        ),
                    )
                } else {
                    AiChatResponse(AiMessage(AiRole.ASSISTANT, "你花了一百万"))
                }
            },
            executor,
        )
        val reply = qa.ask("ledger", "CNY", emptyList(), "这个月花了多少")
        assertEquals("你花了一百万", reply.text)
        assertTrue(reply.citations.single().fact.contains("¥34.50"))
        assertTrue(reply.citations.single().periodLabel.contains("2026年10月"))
    }

    @Test
    fun credentialFileDoesNotContainTheKey() = runBlocking {
        val dir = Files.createTempDirectory("ai-key").toFile()
        val store = AiCredentialStore(File(dir, "ai-credential.bin"), XorBox())
        store.write("sk-unit-test-key-9f3a")
        val stored = File(dir, "ai-credential.bin").readBytes()
        assertFalse(stored.toString(Charsets.UTF_8).contains("sk-unit-test-key-9f3a"))
        val present = store.read()
        assertTrue(present is CredentialRead.Present)
        assertEquals("Present", present.toString())
        assertEquals("sk-unit-test-key-9f3a", (present as CredentialRead.Present).key)
        store.clear()
        assertTrue(store.read() is CredentialRead.Absent)
        File(dir, "ai-credential.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(store.read() is CredentialRead.Unreadable)
    }

    private class FakeSource : LedgerQuerySource {
        var summaryCalls = 0
        override suspend fun summary(ledgerId: String, range: DateTimeRange): IncomeExpenseSummary {
            summaryCalls++
            return IncomeExpenseSummary(incomeMinor = 12_000, expenseMinor = 3_450)
        }

        override suspend fun categories(ledgerId: String, range: DateTimeRange, income: Boolean): List<CategoryTotal> =
            (1..15).map { CategoryTotal("c$it", "分类$it", "icon", it * 100L) }

        override suspend fun topTransactions(
            ledgerId: String,
            range: DateTimeRange,
            type: TransactionType?,
            limit: Int,
        ): List<TransactionDetail> = listOf(9_000L, 1_000L, 5_000L, 200L).map { amount ->
            sampleDetail(amount)
        }

        override suspend fun budgets(ledgerId: String, month: YearMonth): List<Budget> = listOf(
            Budget("b", ledgerId, month, null, 10_000, 0, 0, null),
        )

        private fun sampleDetail(amount: Long) = TransactionDetail(
        transaction = Transaction(
            id = "t$amount",
            ledgerId = "ledger",
            type = TransactionType.EXPENSE,
            amountMinor = amount,
            accountId = "secret-account",
            toAccountId = null,
            categoryId = "food",
            transactionAt = Instant.parse("2026-10-02T00:00:00Z").toEpochMilli(),
            note = "尾号6222021234567890 午饭",
            createdAt = 0,
            updatedAt = 0,
            baseAmountMinor = amount,
        ),
        accountName = "招商银行(6222021234567890123)",
        toAccountName = null,
        categoryName = "餐饮",
            categoryIconKey = "food",
        )
    }

    private class XorBox : SecretBox {
        override fun seal(plain: ByteArray): ByteArray = byteArrayOf(0x7E) + plain.map { (it.toInt() xor 0x3C).toByte() }.toByteArray()
        override fun open(sealed: ByteArray): ByteArray {
            check(sealed.first() == 0x7E.toByte())
            return sealed.copyOfRange(1, sealed.size).map { (it.toInt() xor 0x3C).toByte() }.toByteArray()
        }
    }

    private fun jsonObject(text: String) = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject

    private fun kotlinx.serialization.json.JsonObject.string(name: String) = this[name]?.jsonPrimitive?.content
}
