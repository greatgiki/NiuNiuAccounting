package com.example.localledger.domain.importing

import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportRulesTest {
    @Test
    fun keywordsMapMerchantsToPresetCategories() {
        assertEquals("餐饮", CategoryKeywordMapper.suggestName(TransactionType.EXPENSE, "美团外卖"))
        assertEquals("餐饮", CategoryKeywordMapper.suggestName(TransactionType.EXPENSE, "饿了么"))
        assertEquals("交通", CategoryKeywordMapper.suggestName(TransactionType.EXPENSE, "滴滴出行 地铁"))
        assertEquals("购物", CategoryKeywordMapper.suggestName(TransactionType.EXPENSE, "淘宝 京东"))
        assertEquals("退款", CategoryKeywordMapper.suggestName(TransactionType.INCOME, "美团 外卖订单-退款"))
        assertEquals("工资", CategoryKeywordMapper.suggestName(TransactionType.INCOME, "工资代发"))
        assertEquals("其他", CategoryKeywordMapper.suggestName(TransactionType.EXPENSE, "未知小店"))
    }

    @Test
    fun paymentMethodMapsToExistingAccounts() {
        val accounts = listOf(
            account("cash", "现金", AccountType.CASH),
            account("bank", "银行卡", AccountType.BANK_CARD),
            account("ali", "支付宝", AccountType.E_WALLET),
            account("wx", "微信", AccountType.E_WALLET),
        )
        assertEquals("wx", AccountHintMapper.suggestAccountId("零钱", BillSource.WECHAT, accounts, "bank"))
        assertEquals("bank", AccountHintMapper.suggestAccountId("招商银行储蓄卡(1234)", BillSource.WECHAT, accounts, "wx"))
        assertEquals("ali", AccountHintMapper.suggestAccountId("余额宝", BillSource.ALIPAY, accounts, "cash"))
        assertEquals("bank", AccountHintMapper.suggestAccountId("/", BillSource.WECHAT, accounts, "bank"))
        assertEquals("bank", AccountHintMapper.suggestAccountId("", BillSource.UNIONPAY, accounts, null))
        assertEquals("bank", AccountHintMapper.suggestAccountId("招商银行尾号1234", BillSource.BANK, accounts, "cash"))
    }

    @Test
    fun exactExternalIdAndFuzzyWindow() {
        val known = setOf(ExternalKey("WECHAT", "wx-1"))
        val candidates = listOf(
            MatchCandidate("manual", CandidateKind.TRANSACTION, TransactionType.EXPENSE, 1_250L, 1_000_000L, RecordOrigin.MANUAL, null, null),
            MatchCandidate("draft", CandidateKind.DRAFT, TransactionType.EXPENSE, 1_250L, 1_000_000L, RecordOrigin.NOTIFICATION, null, null),
            MatchCandidate("other", CandidateKind.TRANSACTION, TransactionType.INCOME, 1_250L, 1_000_000L, RecordOrigin.MANUAL, null, null),
        )
        val imported = ImportMatcher.match(BillSource.WECHAT, "wx-1", TransactionType.EXPENSE, 1_250L, 0L, known, candidates)
        assertEquals(DuplicateFlag.ALREADY_IMPORTED, imported.flag)
        assertNull(imported.hit)

        val fuzzy = ImportMatcher.match(BillSource.WECHAT, "wx-2", TransactionType.EXPENSE, 1_250L, 1_200_000L, known, candidates)
        assertEquals(DuplicateFlag.LIKELY_DUPLICATE, fuzzy.flag)
        assertEquals("draft", fuzzy.hit?.candidateId)

        val outside = ImportMatcher.match(
            BillSource.WECHAT,
            "wx-3",
            TransactionType.EXPENSE,
            1_250L,
            1_000_000L + ImportMatcher.WINDOW_MILLIS + 1,
            emptySet(),
            candidates,
        )
        assertEquals(DuplicateFlag.NONE, outside.flag)

        val differentType = ImportMatcher.match(BillSource.WECHAT, "wx-4", TransactionType.INCOME, 1_250L, 1_000_000L, emptySet(), listOf(candidates[0]))
        assertEquals(DuplicateFlag.NONE, differentType.flag)
    }

    private fun account(id: String, name: String, type: AccountType) = Account(
        id = id,
        ledgerId = "ledger",
        name = name,
        type = type,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
