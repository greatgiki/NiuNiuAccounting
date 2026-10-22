package com.example.localledger.domain.voice

import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class VoiceLedgerParserTest {
    private val today = LocalDate.of(2026, 10, 22)

    private val categories = listOf(
        expense("food", "餐饮"),
        expense("traffic", "交通"),
        expense("shop", "购物"),
        expense("home", "居住"),
        expense("fun", "娱乐"),
        expense("med", "医疗"),
        expense("edu", "教育"),
        expense("phone", "通讯"),
        expense("other-e", "其他"),
        income("salary", "工资"),
        income("bonus", "奖金"),
        income("part", "兼职"),
        income("refund", "退款"),
        income("other-i", "其他"),
    )

    private val accounts = listOf(
        account("cash", "现金", AccountType.CASH),
        account("bank", "银行卡", AccountType.BANK_CARD),
        account("ali", "支付宝", AccountType.E_WALLET),
        account("wx", "微信", AccountType.E_WALLET),
    )

    @Test
    fun arabicAndChineseAmounts() {
        assertYuan("35", "三十五")
        assertYuan("120", "一百二")
        assertYuan("120", "一百二十")
        assertYuan("105", "一百零五")
        assertYuan("2500", "两千五")
        assertYuan("3.5", "3块5")
        assertYuan("12.8", "十二块八毛")
        assertYuan("12.85", "十二块八毛五")
        assertYuan("15000", "1.5万")
        assertYuan("12", "十二")
        assertYuan("0.8", "八毛")
        assertYuan("0.05", "五分")
        assertYuan("3.5", "三块半")
        assertYuan("23000", "两万三")
        assertYuan("25000", "2万5")
        assertYuan("3000", "三千")
        assertYuan("3000", "3千")
        assertYuan("10", "十块")
        assertYuan("1.5", "一块五")
        assertYuan("12000", "一万二")
        assertYuan("3.5", "三点五元")
        assertYuan("35", "３５元")
        assertYuan("100", "一百")
        assertYuan("200", "两百")
        assertYuan("1001", "一千零一")
    }

    @Test
    fun incomeKeywordsOverrideTheExpenseDefault() {
        assertType(TransactionType.EXPENSE, "午饭35")
        assertType(TransactionType.INCOME, "收入三千")
        assertType(TransactionType.INCOME, "工资一万二")
        assertEquals("salary", only("工资一万二").categoryId)
        assertYuan("12000", "工资一万二")
        assertType(TransactionType.INCOME, "报销200")
        assertEquals("报销", only("报销200").note)
        assertType(TransactionType.INCOME, "退款35")
        assertEquals("refund", only("退款35").categoryId)
        assertType(TransactionType.INCOME, "红包200")
        assertEquals("红包", only("红包200").note)
        assertType(TransactionType.EXPENSE, "发红包200")
        assertType(TransactionType.EXPENSE, "发了红包200")
        assertType(TransactionType.INCOME, "奖金两千")
        assertEquals("bonus", only("奖金两千").categoryId)
        assertType(TransactionType.INCOME, "收到薪水八千")
        assertEquals("salary", only("收到薪水八千").categoryId)
    }

    @Test
    fun relativeDatesUseTheSuppliedToday() {
        assertEquals(today, only("午饭35").date)
        assertEquals(today.minusDays(1), only("昨天午饭35").date)
        assertEquals(today.minusDays(2), only("前天打车12").date)
        assertEquals(today.minusDays(3), only("大前天咖啡10").date)
        assertEquals(LocalDate.of(2026, 10, 16), only("上周五加油200").date)
        assertEquals(LocalDate.of(2026, 10, 16), only("周五地铁4").date)
        assertEquals(LocalDate.of(2026, 10, 23), only("本周五电影40").date)
        assertEquals(LocalDate.of(2026, 10, 18), only("上周日超市20").date)
        assertEquals(LocalDate.of(2026, 10, 3), only("3号午饭35").date)
        assertEquals(LocalDate.of(2026, 9, 28), only("28号打车20").date)
        assertEquals(LocalDate.of(2026, 10, 5), only("10月5日打车20").date)
        assertEquals(LocalDate.of(2026, 10, 5), only("十月五日午饭35").date)
        assertEquals(LocalDate.of(2025, 10, 25), only("10月25日房租100").date)
        val early = LocalDate.of(2026, 10, 2)
        assertEquals(LocalDate.of(2026, 9, 3), parse("3号午饭35", early).single().date)
    }

    @Test
    fun categoriesMatchNamesSynonymsAndMerchantRules() {
        assertEquals("food", only("午饭35").categoryId)
        assertEquals("food", only("晚饭20").categoryId)
        assertEquals("food", only("外卖18").categoryId)
        assertEquals("traffic", only("打车20").categoryId)
        assertEquals("traffic", only("地铁4").categoryId)
        assertEquals("food", only("美团外卖35").categoryId)
        assertEquals("shop", only("超市80").categoryId)
        assertEquals("home", only("房租2000").categoryId)
        assertEquals("traffic", only("上周五加油200").categoryId)

        val custom = categories + expense("pet", "宠物")
        val pet = parse("宠物零食40", categories = custom).single()
        assertEquals("pet", pet.categoryId)
        assertEquals("零食", pet.note)

        val lunch = categories + expense("lunch", "午餐")
        assertEquals("lunch", parse("午餐35", categories = lunch).single().categoryId)

        val withoutFood = categories.filter { it.id != "food" }
        val guessed = parse("午饭35", categories = withoutFood).single()
        assertNull(guessed.categoryId)
        assertEquals("餐饮", guessed.categoryName)
    }

    @Test
    fun accountNamesAndASingleCard() {
        assertEquals("wx", only("微信午饭35").accountId)
        assertEquals("ali", only("支付宝打车20").accountId)
        assertEquals("bank", only("银行卡超市80").accountId)
        assertEquals("cash", only("现金买菜12").accountId)

        val cmb = listOf(account("cmb", "招商银行", AccountType.BANK_CARD))
        assertEquals("cmb", parse("用卡付了35", accounts = cmb).single().accountId)

        val twoCards = listOf(
            account("a", "招商银行", AccountType.BANK_CARD),
            account("b", "工商银行", AccountType.BANK_CARD),
        )
        assertNull(parse("刷卡35", accounts = twoCards).single().accountId)
        assertEquals("a", parse("招商银行午饭35", accounts = twoCards).single().accountId)
    }

    @Test
    fun leftoverWordsBecomeTheNote() {
        val draft = only("昨天在公司附近午饭三十五微信")
        assertEquals("在公司附近", draft.note)
        assertEquals("wx", draft.accountId)
        assertEquals("food", draft.categoryId)
        assertYuan("35", draft)
        assertEquals(today.minusDays(1), draft.date)
        assertEquals("", only("花了三十五午饭").note)
        assertEquals("在公司附近", only("在公司附近的午饭35").note)
    }

    @Test
    fun separatorsProduceOneDraftPerItem() {
        listOf("午饭35，打车20", "午饭35和打车20", "午饭35还有打车20", "午饭35、打车20").forEach { utterance ->
            val drafts = parse(utterance)
            assertEquals(utterance, 2, drafts.size)
            assertEquals("food", drafts[0].categoryId)
            assertYuan("35", drafts[0])
            assertEquals("traffic", drafts[1].categoryId)
            assertYuan("20", drafts[1])
        }
        assertEquals(1, parse("我和朋友午饭35").size)
        assertYuan("35", "我和朋友午饭35")

        val shared = parse("用微信，午饭35，打车20")
        assertEquals(listOf("wx", "wx"), shared.map { it.accountId })
        assertEquals(listOf("food", "traffic"), shared.map { it.categoryId })

        val dated = parse("昨天午饭35，打车20")
        assertTrue(dated.all { it.date == today.minusDays(1) })

        val mixed = parse("午饭35，工资5000")
        assertEquals(TransactionType.EXPENSE, mixed[0].type)
        assertEquals(TransactionType.INCOME, mixed[1].type)
        assertEquals("salary", mixed[1].categoryId)
        assertYuan("5000", mixed[1])

        val placed = parse("在公司，午饭35，打车20")
        assertTrue(placed.all { it.note == "在公司" })
    }

    @Test
    fun blankUtteranceIsEmptyAndUnknownTextStaysEditable() {
        assertTrue(parse("   ").isEmpty())
        assertTrue(parse("").isEmpty())
        val hello = only("你好")
        assertNull(hello.amount)
        assertEquals("你好", hello.note)
        assertEquals(TransactionType.EXPENSE, hello.type)
        assertEquals(today, hello.date)
    }

    private fun assertYuan(expected: String, utterance: String) {
        assertYuan(expected, only(utterance))
    }

    private fun assertYuan(expected: String, draft: VoiceLedgerDraft) {
        val amount = draft.amount
        assertTrue("expected $expected but was $amount for ${draft.sourceText}", amount != null && BigDecimal(expected).compareTo(amount) == 0)
    }

    private fun assertType(type: TransactionType, utterance: String) {
        assertEquals(utterance, type, only(utterance).type)
    }

    private fun only(utterance: String) = parse(utterance).single()

    private fun parse(
        utterance: String,
        day: LocalDate = today,
        categories: List<VoiceCategoryRef> = this.categories,
        accounts: List<VoiceAccountRef> = this.accounts,
    ) = VoiceLedgerParser.parse(utterance, categories, accounts, day)

    private fun expense(id: String, name: String) = VoiceCategoryRef(id, name, CategoryType.EXPENSE)

    private fun income(id: String, name: String) = VoiceCategoryRef(id, name, CategoryType.INCOME)

    private fun account(id: String, name: String, type: AccountType) = VoiceAccountRef(id, name, type)
}
