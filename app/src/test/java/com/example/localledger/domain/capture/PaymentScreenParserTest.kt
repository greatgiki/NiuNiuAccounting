package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PaymentScreenParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 26, 13, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun wechatSuccessPageReadsAmountMerchantAndAccount() {
        val parsed = parse(
            BillSource.WECHAT,
            """
            支付成功
            ¥12.50
            星巴克
            支付方式
            零钱
            完成
            """.trimIndent(),
        )
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals(1_250L, parsed?.amountMinor)
        assertEquals("星巴克", parsed?.merchant)
        assertEquals("零钱", parsed?.accountLabel)
        assertEquals("wechat-pay-success", parsed?.ruleId)
        assertEquals(CapturePage.SUCCESS, parsed?.page)
    }

    @Test
    fun wechatBillDetailUsesLabelsAndPaymentTime() {
        val parsed = parse(
            BillSource.WECHAT,
            """
            账单详情
            当前状态
            支付成功
            支付时间
            2026年10月26日 12:08:01
            商户全称
            星巴克咖啡
            支付方式
            招商银行储蓄卡(1234)
            ¥28.00
            """.trimIndent(),
        )
        val expected = ZonedDateTime.of(2026, 10, 26, 12, 8, 1, 0, zone).toInstant().toEpochMilli()
        assertEquals(CapturePage.DETAIL, parsed?.page)
        assertEquals("wechat-bill-detail", parsed?.ruleId)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals(2_800L, parsed?.amountMinor)
        assertEquals("星巴克咖啡", parsed?.merchant)
        assertEquals("招商银行储蓄卡(1234)", parsed?.accountLabel)
        assertEquals(expected, parsed?.occurredAt)
    }

    @Test
    fun wechatTransferAndIncomePages() {
        val transfer = parse(
            BillSource.WECHAT,
            """
            转账成功
            ¥100.00
            李雷
            """.trimIndent(),
        )
        assertEquals(TransactionType.TRANSFER, transfer?.type)
        assertEquals("李雷", transfer?.merchant)
        val income = parse(
            BillSource.WECHAT,
            """
            收款成功
            ¥20.00
            付款方
            韩梅梅
            """.trimIndent(),
        )
        assertEquals(TransactionType.INCOME, income?.type)
        assertEquals(2_000L, income?.amountMinor)
        assertEquals("韩梅梅", income?.merchant)
    }

    @Test
    fun alipaySuccessAndBillDetail() {
        val success = parse(
            BillSource.ALIPAY,
            """
            付款成功
            18.00
            星巴克
            付款方式
            余额宝
            """.trimIndent(),
        )
        assertEquals("alipay-pay-success", success?.ruleId)
        assertEquals(TransactionType.EXPENSE, success?.type)
        assertEquals(1_800L, success?.amountMinor)
        assertEquals("余额宝", success?.accountLabel)
        val detail = parse(
            BillSource.ALIPAY,
            """
            账单详情
            交易成功
            创建时间
            2026-10-26 12:08
            交易对方
            美团
            付款方式
            花呗
            订单金额
            ¥31.20
            """.trimIndent(),
        )
        assertEquals("alipay-bill-detail", detail?.ruleId)
        assertEquals(3_120L, detail?.amountMinor)
        assertEquals("美团", detail?.merchant)
        assertEquals("花呗", detail?.accountLabel)
        assertEquals(
            ZonedDateTime.of(2026, 10, 26, 12, 8, 0, 0, zone).toInstant().toEpochMilli(),
            detail?.occurredAt,
        )
    }

    @Test
    fun unionPaySuccessPage() {
        val parsed = parse(
            BillSource.UNIONPAY,
            """
            交易成功
            ￥66.00
            商户名称
            地铁
            付款方式
            招商银行信用卡(8899)
            """.trimIndent(),
        )
        assertEquals("unionpay-pay-success", parsed?.ruleId)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals(6_600L, parsed?.amountMinor)
        assertEquals("地铁", parsed?.merchant)
        assertEquals("招商银行信用卡(8899)", parsed?.accountLabel)
    }

    @Test
    fun commaAmountAndRefund() {
        val parsed = parse(
            BillSource.WECHAT,
            """
            退款成功
            ¥1,250.50
            星巴克
            """.trimIndent(),
        )
        assertEquals(TransactionType.INCOME, parsed?.type)
        assertEquals(125_050L, parsed?.amountMinor)
    }

    @Test
    fun passwordChatPendingAndClosedPagesAreIgnored() {
        assertNull(
            parse(
                BillSource.WECHAT,
                """
                请输入支付密码
                pwd:secret
                  123456
                ¥12.50
                """.trimIndent(),
            ),
        )
        val passwordTree = ScreenDump.parse(
            """
            请输入支付密码
            pwd:secret
              123456
            """.trimIndent(),
        )
        val visible = ScreenText.lines(passwordTree).joinToString("\n")
        assertFalse(visible.contains("secret"))
        assertFalse(visible.contains("123456"))
        assertNull(
            parse(
                BillSource.WECHAT,
                """
                李雷
                晚上吃饭吗
                发送
                """.trimIndent(),
            ),
        )
        assertNull(
            parse(
                BillSource.WECHAT,
                """
                待朋友确认收钱
                ¥1.00
                """.trimIndent(),
            ),
        )
        assertNull(
            parse(
                BillSource.ALIPAY,
                """
                账单详情
                交易关闭
                ¥9.90
                美团
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun passwordFieldIsDroppedEvenOnASuccessPage() {
        val lines = ScreenText.lines(
            ScreenDump.parse(
                """
                支付成功
                ¥8.00
                pwd:000000
                星巴克
                """.trimIndent(),
            ),
        )
        assertFalse(lines.any { "000000" in it })
        val parsed = PaymentScreenParser.parse(
            BillSource.WECHAT,
            ScreenDump.parse(
                """
                支付成功
                ¥8.00
                pwd:000000
                星巴克
                """.trimIndent(),
            ),
            now,
            zone,
        )
        assertEquals("星巴克", parsed?.merchant)
        assertEquals(800L, parsed?.amountMinor)
    }

    @Test
    fun wechatServiceNoticeReadsPlainAmountAndMerchant() {
        val expense = parse(
            BillSource.WECHAT,
            """
            服务通知
            微信支付
            支付凭证
            商户名称
            星巴克
            订单金额
            ¥100.00
            实付金额
            ¥88.00
            支付方式
            零钱
            """.trimIndent(),
        )
        assertEquals("wechat-service-notice", expense?.ruleId)
        assertEquals(TransactionType.EXPENSE, expense?.type)
        assertEquals(8_800L, expense?.amountMinor)
        assertEquals("星巴克", expense?.merchant)
        assertEquals("零钱", expense?.accountLabel)
        val income = parse(
            BillSource.WECHAT,
            """
            服务通知
            微信支付
            收款到账通知
            收款金额
            ¥20.00
            付款方
            韩梅梅
            """.trimIndent(),
        )
        assertEquals(TransactionType.INCOME, income?.type)
        assertEquals(2_000L, income?.amountMinor)
        assertEquals("韩梅梅", income?.merchant)
        val card = parse(
            BillSource.WECHAT,
            """
            微信支付
            支付凭证
            星巴克
            ¥12.50
            """.trimIndent(),
        )
        assertEquals(1_250L, card?.amountMinor)
        assertEquals("星巴克", card?.merchant)
    }

    @Test
    fun serviceNoticeIgnoresChatAndMaskedAmount() {
        assertNull(
            parse(
                BillSource.WECHAT,
                """
                张三
                微信支付怎么样
                发送
                """.trimIndent(),
            ),
        )
        assertNull(
            parse(
                BillSource.WECHAT,
                """
                服务通知
                微信支付
                支付凭证
                消费金额
                **元
                星巴克
                """.trimIndent(),
            ),
        )
    }

    private fun parse(source: BillSource, dump: String) =
        PaymentScreenParser.parse(source, ScreenDump.parse(dump), now, zone)
}

class BankSmsParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val receivedAt = ZonedDateTime.of(2026, 10, 26, 21, 9, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun expenseKeepsMerchantAndIgnoresBalance() {
        val parsed = BankSmsParser.parse(
            "【招商银行】您尾号1234的储蓄卡于10月26日21:08支出人民币12.50元，商户：星巴克。余额100.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals(1_250L, parsed?.amountMinor)
        assertEquals("星巴克", parsed?.merchant)
        assertEquals("招商银行尾号1234", parsed?.accountLabel)
        assertEquals("bank-sms", parsed?.ruleId)
        assertEquals(
            ZonedDateTime.of(2026, 10, 26, 21, 8, 0, 0, zone).toInstant().toEpochMilli(),
            parsed?.occurredAt,
        )
    }

    @Test
    fun incomeRefundAndTransferToSelf() {
        val income = BankSmsParser.parse(
            "【工商银行】您尾号8901卡10月26日21:08网上银行收入（退款）人民币20.00元，余额100.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.INCOME, income?.type)
        assertEquals(2_000L, income?.amountMinor)
        val transfer = BankSmsParser.parse(
            "【建设银行】您尾号5566的储蓄卡10月26日21:08转入本人账户人民币100.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.TRANSFER, transfer?.type)
        assertEquals(10_000L, transfer?.amountMinor)
    }

    @Test
    fun payToMerchantAndVerificationCode() {
        val parsed = BankSmsParser.parse(
            "【建设银行】您尾号5566的储蓄卡10月26日21:08向美团支付人民币18.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("美团", parsed?.merchant)
        assertEquals(1_800L, parsed?.amountMinor)
        assertNull(BankSmsParser.parse("【招商银行】验证码123456，请勿泄露。", receivedAt, zone))
        assertNull(BankSmsParser.parse("今晚吃饭吗", receivedAt, zone))
    }

    @Test
    fun mainlandBanksKeepTailDirectionAndIgnoreBalance() {
        val icbc = BankSmsParser.parse(
            "【工商银行】您尾号为8901的储蓄卡于10月26日21:08支出人民币12.50元，商户：星巴克。余额88.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, icbc?.type)
        assertEquals(1_250L, icbc?.amountMinor)
        assertEquals("星巴克", icbc?.merchant)
        assertEquals("工商银行尾号8901", icbc?.accountLabel)
        val ccb = BankSmsParser.parse(
            "【建设银行】您尾号5566的储蓄卡(5566)于10月26日21:08向美团支付人民币18.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, ccb?.type)
        assertEquals("美团", ccb?.merchant)
        assertEquals(1_800L, ccb?.amountMinor)
        val abc = BankSmsParser.parse(
            "【农业银行】您尾号3344账户10月26日21:08完成交易，支出金额人民币66.00元，余额200.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, abc?.type)
        assertEquals(6_600L, abc?.amountMinor)
        assertEquals("农业银行尾号3344", abc?.accountLabel)
        val boc = BankSmsParser.parse(
            "【中国银行】您尾号7788借记卡于10月26日21:08支出CNY30.00，余额CNY100.00。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, boc?.type)
        assertEquals(3_000L, boc?.amountMinor)
        assertEquals("中国银行尾号7788", boc?.accountLabel)
        val cmb = BankSmsParser.parse(
            "【招商银行】您尾号1234的储蓄卡于10月26日21:08入账人民币20.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.INCOME, cmb?.type)
        assertEquals(2_000L, cmb?.amountMinor)
        val bocom = BankSmsParser.parse(
            "【交通银行】您尾号2468卡于10月26日21:08扣款人民币15.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.EXPENSE, bocom?.type)
        assertEquals(1_500L, bocom?.amountMinor)
        assertEquals("交通银行尾号2468", bocom?.accountLabel)
        val psbc = BankSmsParser.parse(
            "【邮储银行】您尾号1357账户10月26日21:08收入人民币9.90元，余额50.00元。",
            receivedAt,
            zone,
        )
        assertEquals(TransactionType.INCOME, psbc?.type)
        assertEquals(990L, psbc?.amountMinor)
        assertEquals("邮储银行尾号1357", psbc?.accountLabel)
    }
}

class CaptureDedupTest {
    @Test
    fun sameAmountInsideAMinutePrompts() {
        val now = 1_000_000L
        val sightings = listOf(
            MoneySighting(1_250L, now - 30_000L, SightPlace.DRAFT, "draft-1"),
            MoneySighting(800L, now - 1_000L, SightPlace.TRANSACTION),
        )
        val hit = CaptureDedup.match(1_250L, TransactionType.EXPENSE, now, sightings)
        assertEquals("draft-1", hit?.draftId)
        assertEquals(SightPlace.DRAFT, hit?.place)
    }

    @Test
    fun boundaryIsInclusiveAndOlderOrDifferentAmountsPass() {
        val now = 5_000_000L
        val edge = MoneySighting(1_250L, now - CaptureDedup.WINDOW_MILLIS, SightPlace.TRANSACTION)
        assertEquals(edge, CaptureDedup.match(1_250L, TransactionType.EXPENSE, now, listOf(edge)))
        val older = MoneySighting(1_250L, now - CaptureDedup.WINDOW_MILLIS - 1, SightPlace.TRANSACTION)
        assertNull(CaptureDedup.match(1_250L, TransactionType.EXPENSE, now, listOf(older)))
        assertNull(CaptureDedup.match(1_251L, TransactionType.EXPENSE, now, listOf(edge)))
        assertNull(CaptureDedup.match(0L, TransactionType.EXPENSE, now, listOf(edge)))
    }

    @Test
    fun closestSightingWins() {
        val now = 90_000L
        val far = MoneySighting(500L, now - 50_000L, SightPlace.TRANSACTION, "old")
        val near = MoneySighting(500L, now - 2_000L, SightPlace.DRAFT, "new")
        assertEquals("new", CaptureDedup.match(500L, TransactionType.EXPENSE, now, listOf(far, near))?.draftId)
    }

    @Test
    fun threeMinuteWindowUsesDirection() {
        assertEquals(180_000L, CaptureDedup.WINDOW_MILLIS)
        val now = 1_000_000L
        val expense = MoneySighting(1_250L, now - 120_000L, SightPlace.TRANSACTION, direction = TransactionType.EXPENSE)
        val income = expense.copy(direction = TransactionType.INCOME)
        assertEquals(expense, CaptureDedup.match(1_250L, TransactionType.EXPENSE, now, listOf(expense, income)))
        assertNull(CaptureDedup.match(1_250L, TransactionType.INCOME, now, listOf(expense)))
        val tooOld = expense.copy(at = now - CaptureDedup.WINDOW_MILLIS - 1)
        assertNull(CaptureDedup.match(1_250L, TransactionType.EXPENSE, now, listOf(tooOld)))
    }

    @Test
    fun fillTargetPrefersTheOnlyMatchingDraft() {
        val now = 5_000_000L
        val wechat = FillCandidate("wx", TransactionType.EXPENSE, now - 30_000L, BillSource.WECHAT, "星巴克")
        val alipay = FillCandidate("ali", TransactionType.EXPENSE, now - 10_000L, BillSource.ALIPAY)
        val income = FillCandidate("in", TransactionType.INCOME, now - 5_000L, BillSource.WECHAT)
        assertEquals(
            "wx",
            CaptureDedup.fillTarget(TransactionType.EXPENSE, now, listOf(wechat, alipay, income), BillSource.WECHAT)?.id,
        )
        assertEquals(
            "wx",
            CaptureDedup.fillTarget(TransactionType.EXPENSE, now, listOf(wechat), BillSource.BANK)?.id,
        )
        val other = FillCandidate("other", TransactionType.EXPENSE, now - 20_000L, BillSource.WECHAT)
        assertNull(CaptureDedup.fillTarget(TransactionType.EXPENSE, now, listOf(wechat, other), BillSource.WECHAT))
        assertEquals(
            "wx",
            CaptureDedup.fillTarget(
                TransactionType.EXPENSE,
                now,
                listOf(wechat, other),
                BillSource.BANK,
                merchant = "星巴克",
            )?.id,
        )
        val stale = wechat.copy(at = now - CaptureDedup.WINDOW_MILLIS - 1)
        assertNull(CaptureDedup.fillTarget(TransactionType.EXPENSE, now, listOf(stale), BillSource.WECHAT))
    }
}

class CaptureSupportTest {
    @Test
    fun hyperOsDefersOverlayUntilThePaymentAppCloses() {
        assertTrue(OverlayDeferral.whilePaymentAppIsOpen("Xiaomi", "Redmi"))
        assertTrue(OverlayDeferral.whilePaymentAppIsOpen("POCO", "POCO"))
        assertTrue(OverlayDeferral.whilePaymentAppIsOpen("blackshark", "blackshark"))
        assertFalse(OverlayDeferral.whilePaymentAppIsOpen("Google", "Pixel"))
    }

    @Test
    fun learnedAccountKeepsTheLatestAndCapsTheMap() {
        val key = LearnedAccount.key("ledger", TransactionType.EXPENSE, " 星巴克 ")
        assertEquals("ledger|EXPENSE|星巴克", key)
        assertEquals("", LearnedAccount.key("ledger", TransactionType.TRANSFER, "星巴克"))
        val once = LearnedAccount.remember(emptyMap(), key, "wx")
        val twice = LearnedAccount.remember(once, key, "bank")
        assertEquals("bank", twice[key])
        assertEquals(1, twice.size)
        var map = emptyMap<String, String>()
        repeat(LearnedAccount.LIMIT + 5) { index ->
            map = LearnedAccount.remember(map, "ledger|EXPENSE|m$index", "acct")
        }
        assertEquals(LearnedAccount.LIMIT, map.size)
        assertEquals("acct", map["ledger|EXPENSE|m${LearnedAccount.LIMIT + 4}"])
        assertFalse(map.containsKey("ledger|EXPENSE|m0"))
    }

    @Test
    fun categoryHintUsesOnlyTheGivenName() {
        val names = listOf("餐饮", "交通", "其他")
        val request = CategoryHintPrompt.request("星巴克", TransactionType.EXPENSE, names)
        val prompt = request.messages.joinToString("\n") { it.text }
        assertTrue(prompt.contains("星巴克"))
        assertTrue(prompt.contains("餐饮"))
        assertFalse(prompt.contains("支付成功"))
        assertEquals("餐饮", CategoryHintPrompt.parse("餐饮。", names))
        assertEquals("其他", CategoryHintPrompt.parse("其他", names))
        assertNull(CategoryHintPrompt.parse("我觉得是餐饮", names))
        assertNull(CategoryHintPrompt.parse("医疗", names))
    }

    @Test
    fun captureFlagsFollowTheMasterSwitch() {
        val off = CaptureFlags.from(com.example.localledger.core.storage.AppSettings("ledger"))
        assertFalse(off.master)
        assertFalse(off.allows(PaymentPackages.WECHAT))
        val on = off.copy(master = true, loaded = true, unionPay = false, bankSms = true)
        assertTrue(on.allows(PaymentPackages.WECHAT))
        assertTrue(on.allows(PaymentPackages.ALIPAY))
        assertFalse(on.allows(PaymentPackages.UNIONPAY))
        assertFalse(on.allows("com.android.mms"))
        assertTrue(BankPackages.isBankApp("cmb.pb"))
        assertTrue(BankPackages.isBankApp("com.icbc"))
        assertTrue(BankPackages.isBankApp("com.yitong.mbank.psbc"))
        assertFalse(BankPackages.isBankApp("com.tencent.mm"))
        assertFalse(BankPackages.isBankApp("com.icbc.evil"))
    }
}
