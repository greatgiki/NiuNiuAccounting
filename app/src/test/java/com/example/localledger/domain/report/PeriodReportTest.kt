package com.example.localledger.domain.report

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryFrequency
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.LargestExpense
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.time.inclusiveDates
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodReportTest {
    @Test
    fun weekDeltasUseSignedAbsoluteAndPercent() {
        val lower = PeriodDelta(currentMinor = 8_800, previousMinor = 10_000)
        assertEquals(-1_200L, lower.absoluteMinor)
        assertEquals(BigDecimal("-12.0"), lower.signedPercent)
        assertEquals("12%", lower.magnitudePercentLabel())

        val higher = PeriodDelta(currentMinor = 11_250, previousMinor = 10_000)
        assertEquals(1_250L, higher.absoluteMinor)
        assertEquals("12.5%", higher.magnitudePercentLabel())
    }

    @Test
    fun percentIsAbsentWhenThePreviousAmountIsZero() {
        val delta = PeriodDelta(currentMinor = 8_800, previousMinor = 0)
        assertEquals(8_800L, delta.absoluteMinor)
        assertNull(delta.signedPercent)
        assertNull(delta.magnitudePercentLabel())
    }

    @Test(expected = LedgerException.ArithmeticOverflow::class)
    fun absoluteDeltaRefusesOverflow() {
        PeriodDelta(Long.MAX_VALUE, -1).absoluteMinor
    }

    @Test
    fun proseMatchesTheQuietWeekTemplate() {
        val text = ReportSummary.prose(
            ProseInput(
                kind = ReportKind.WEEK,
                incomeMinor = 20_000,
                expenseMinor = 8_800,
                previousExpenseMinor = 10_000,
                topCategoryName = "餐饮",
                previousTopCategoryName = "餐饮",
                spikeDayLabel = null,
                budgetSentence = null,
            ),
        )
        assertEquals("本周支出比上周少 12%，餐饮仍是最大类别。", text)
    }

    @Test
    fun monthProseUsesLastMonthAndANewTopCategory() {
        val text = ReportSummary.prose(
            ProseInput(
                kind = ReportKind.MONTH,
                incomeMinor = 0,
                expenseMinor = 8_800,
                previousExpenseMinor = 10_000,
                topCategoryName = "购物",
                previousTopCategoryName = "餐饮",
                spikeDayLabel = null,
                budgetSentence = null,
            ),
        )
        assertEquals("本月支出比上月少 12%，购物是最大支出类别。", text)
    }

    @Test
    fun emptyWeekStaysASingleSentence() {
        assertEquals(
            "本周没有收支记录。",
            ReportSummary.prose(
                ProseInput(
                    kind = ReportKind.WEEK,
                    incomeMinor = 0,
                    expenseMinor = 0,
                    previousExpenseMinor = 0,
                    topCategoryName = null,
                    previousTopCategoryName = null,
                    spikeDayLabel = null,
                    budgetSentence = null,
                ),
            ),
        )
    }

    @Test
    fun daySpikeNeedsTwiceThePriorDailyAverageAndARealGap() {
        assertTrue(ReportSpikes.dayIsSpike(dayExpense = 30_000, previousExpense = 70_000, previousDays = 7))
        assertFalse(ReportSpikes.dayIsSpike(dayExpense = 19_000, previousExpense = 70_000, previousDays = 7))
        assertFalse(ReportSpikes.dayIsSpike(dayExpense = 30, previousExpense = 70, previousDays = 7))
        assertFalse(ReportSpikes.dayIsSpike(dayExpense = 30_000, previousExpense = 0, previousDays = 7))
        assertTrue(ReportSpikes.categoryIsSpike(current = 20_000, previous = 8_000))
        assertFalse(ReportSpikes.categoryIsSpike(current = 12_000, previous = 8_000))
    }

    @Test
    fun isoWeekLabelCoversMondayToSundayIncludingYearBoundaries() {
        assertEquals(
            "2026年第30周 · 7月20日–26日",
            weekPeriodLabel(LocalDate.of(2026, 7, 20)),
        )
        assertEquals(
            "2026年第14周 · 3月30日–4月5日",
            weekPeriodLabel(LocalDate.of(2026, 3, 30)),
        )
        assertEquals(
            "2026年第1周 · 2025年12月29日–2026年1月4日",
            weekPeriodLabel(LocalDate.of(2025, 12, 29)),
        )
        assertEquals(
            "2026年第53周 · 2026年12月28日–2027年1月3日",
            weekPeriodLabel(LocalDate.of(2026, 12, 28)),
        )
    }

    @Test
    fun anchorSnapsToMondayOrMonthStartAndStepsInsideRange() {
        assertEquals(LocalDate.of(2026, 7, 20), normalizeAnchor(ReportKind.WEEK, LocalDate.of(2026, 7, 22)))
        assertEquals(DayOfWeek.MONDAY, normalizeAnchor(ReportKind.WEEK, LocalDate.of(2026, 7, 22)).dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 1), normalizeAnchor(ReportKind.MONTH, LocalDate.of(2026, 7, 22)))
        assertEquals(
            LocalDate.of(2026, 7, 20),
            initialAnchor(ReportKind.WEEK, "2026-07-22", LocalDate.of(2026, 1, 1)),
        )
        assertEquals(
            LocalDate.of(2026, 8, 1),
            initialAnchor(ReportKind.MONTH, "not-a-date", LocalDate.of(2026, 8, 19)),
        )
        assertEquals(LocalDate.of(2026, 7, 13), shiftAnchor(ReportKind.WEEK, LocalDate.of(2026, 7, 22), -1))
        assertEquals(LocalDate.of(2026, 8, 1), shiftAnchor(ReportKind.MONTH, LocalDate.of(2026, 7, 22), 1))
        assertNull(shiftAnchor(ReportKind.WEEK, LocalDate.of(9999, 12, 27), 8))
        assertEquals(
            LocalDate.of(2026, 7, 20) to LocalDate.of(2026, 7, 26),
            StatisticsPeriod.Week.containing(LocalDate.of(2026, 7, 22)).inclusiveDates(),
        )
        assertEquals(
            LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28),
            StatisticsPeriod.Month(YearMonth.of(2026, 2)).inclusiveDates(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun weekRejectsAStartThatIsNotMonday() {
        StatisticsPeriod.Week(LocalDate.of(2026, 7, 22))
    }

    @Test
    fun reportsStayHiddenUntilUnlock() {
        assertFalse(reportContentVisible(ReportLock.Unknown))
        assertFalse(reportContentVisible(ReportLock.Locked))
        assertTrue(reportContentVisible(ReportLock.Unlocked))
        assertEquals(ReportKind.WEEK, reportKindOrNull("week"))
        assertEquals(ReportKind.MONTH, reportKindOrNull("month"))
        assertNull(reportKindOrNull("year"))
    }

    @Test
    fun composerBuildsTheWeekSummaryComparisonsAndShareText() {
        val report = PeriodReportComposer.compose(weekFacts())
        assertEquals("本周支出比上周少 12%，餐饮仍是最大类别。", report.prose)
        assertEquals("支出较上周少 ¥12.00（12%）", report.expenseComparison)
        assertEquals("收入与上周持平", report.incomeComparison)
        assertEquals("结余较上周多 ¥12.00（12%）", report.balanceComparison)
        assertEquals(7, report.days.size)
        assertEquals(LocalDate.of(2026, 7, 20), report.days.first().date)
        assertEquals(LocalDate.of(2026, 7, 26), report.days.last().date)
        assertTrue(report.weeks.isEmpty())
        assertEquals("最大一笔", report.highlights.first().title)
        assertTrue(report.highlights.any { it.title == "最常记录" && it.body.startsWith("餐饮 · 4 笔") })
        assertFalse(report.highlights.any { it.kind == HighlightKind.BUDGET })
        assertEquals(EXPECTED_SHARE.trim(), report.shareText)
        listOf("密码", "指纹", "PIN", "pin", "应用锁").forEach { secret ->
            assertFalse(report.shareText.contains(secret))
            assertFalse(report.prose.contains(secret))
        }
    }

    @Test
    fun monthReportMentionsBudgetProgressAndASpike() {
        val report = PeriodReportComposer.compose(
            weekFacts().copy(
                kind = ReportKind.MONTH,
                start = LocalDate.of(2026, 7, 1),
                endInclusive = LocalDate.of(2026, 7, 31),
                expenseMinor = 30_000,
                previousExpenseMinor = 70_000,
                incomeMinor = 0,
                previousIncomeMinor = 0,
                days = listOf(DailyTotal(LocalDate.of(2026, 7, 21), incomeMinor = 0, expenseMinor = 30_000)),
                expenseCategories = listOf(category("food", "餐饮", 30_000)),
                previousExpenseCategories = listOf(category("food", "餐饮", 8_000)),
                budgets = listOf(
                    Budget(
                        id = "budget",
                        ledgerId = "ledger",
                        yearMonth = YearMonth.of(2026, 7),
                        categoryId = null,
                        amountMinor = 10_000,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )
        assertTrue(report.prose.contains("本月支出比上月少 57.1%"))
        assertTrue(report.prose.contains("餐饮仍是最大类别"))
        assertTrue(report.prose.contains("7月21日支出明显高于上月日均"))
        assertTrue(report.prose.contains("总预算已超出"))
        assertEquals(31, report.days.size)
        assertTrue(report.weeks.size > 1)
        assertEquals(30_000L, report.weeks.sumOf { it.expenseMinor })
        assertTrue(report.highlights.any { it.kind == HighlightKind.BUDGET && it.body.contains("已超出") })
        assertTrue(report.highlights.any { it.kind == HighlightKind.SPIKE })
        assertTrue(report.shareText.contains("总预算已超出"))
        assertFalse(report.shareText.contains("应用锁"))
    }

    @Test
    fun monthInsideBudgetUsesAQuietPercent() {
        val report = PeriodReportComposer.compose(
            weekFacts().copy(
                kind = ReportKind.MONTH,
                start = LocalDate.of(2026, 7, 1),
                endInclusive = LocalDate.of(2026, 7, 31),
                expenseMinor = 8_000,
                previousExpenseMinor = 8_000,
                budgets = listOf(
                    Budget(
                        id = "budget",
                        ledgerId = "ledger",
                        yearMonth = YearMonth.of(2026, 7),
                        categoryId = null,
                        amountMinor = 10_000,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )
        assertEquals("本月支出与上月持平，餐饮仍是最大类别。总预算已用 80%。", report.prose)
        assertTrue(report.highlights.any { it.body == "总预算已用 80%，剩余 ¥20.00" })
    }

    private fun weekFacts() = ReportFacts(
        kind = ReportKind.WEEK,
        ledgerName = "日常账本",
        currencyCode = "CNY",
        start = LocalDate.of(2026, 7, 20),
        endInclusive = LocalDate.of(2026, 7, 26),
        incomeMinor = 20_000,
        expenseMinor = 8_800,
        previousIncomeMinor = 20_000,
        previousExpenseMinor = 10_000,
        expenseCategories = listOf(category("food", "餐饮", 5_000)),
        previousExpenseCategories = listOf(category("food", "餐饮", 6_000)),
        largestExpense = LargestExpense(
            categoryName = "餐饮",
            amountMinor = 3_200,
            date = LocalDate.of(2026, 7, 21),
            note = null,
        ),
        expenseCounts = listOf(
            CategoryFrequency(
                categoryId = "food",
                categoryName = "餐饮",
                iconKey = "restaurant",
                count = 4,
                amountMinor = 5_000,
            ),
        ),
    )

    private fun category(id: String, name: String, amount: Long) = CategoryTotal(
        categoryId = id,
        categoryName = name,
        iconKey = "restaurant",
        amountMinor = amount,
    )

    private companion object {
        const val EXPECTED_SHARE = """
牛牛记账周报
日常账本 · 人民币（CNY）
2026年第30周 · 7月20日–26日

本周支出比上周少 12%，餐饮仍是最大类别。

收入 ¥200.00
支出 ¥88.00
结余 ¥112.00
收入与上周持平
支出较上周少 ¥12.00（12%）
结余较上周多 ¥12.00（12%）
最大类别 餐饮 ¥50.00
最大一笔 餐饮 ¥32.00
"""
    }
}
