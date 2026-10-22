package com.example.localledger.domain.calendar

import com.example.localledger.domain.importing.CategoryGuess
import com.example.localledger.domain.importing.MerchantKey
import com.example.localledger.domain.importing.MerchantMemoryPolicy
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.widget.WidgetPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarAndWidgetTest {
    @Test
    fun monthGridStartsOnMondayAndKeepsSixWeeks() {
        val cells = CalendarGrid.cells(YearMonth.of(2026, 10), emptyMap())
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
        assertFalse(cells.first().inMonth)
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
        assertTrue(cells[3].inMonth)
        assertEquals(LocalDate.of(2026, 11, 8), cells.last().date)
    }

    @Test
    fun dayBucketUsesTheLedgerZone() {
        val zone = ZoneId.of("Asia/Shanghai")
        val at = LocalDate.of(2026, 10, 21).atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = CalendarGrid.bucket(
            listOf(
                DatedAmount(at, TransactionType.EXPENSE, 1_250),
                DatedAmount(at + 3_600_000, TransactionType.INCOME, 500),
                DatedAmount(at, TransactionType.TRANSFER, 9_999),
            ),
            zone,
        )
        val day = totals.getValue(LocalDate.of(2026, 10, 21))
        assertEquals(500L, day.incomeMinor)
        assertEquals(1_250L, day.expenseMinor)
        assertEquals(1, totals.size)
    }

    @Test
    fun compactYuanStaysShort() {
        assertEquals("", CalendarGrid.compactYuan(0))
        assertEquals("12", CalendarGrid.compactYuan(1_250))
        assertEquals("1万", CalendarGrid.compactYuan(1_000_000))
        assertEquals("0.5", CalendarGrid.compactYuan(50))
    }

    @Test
    fun pagerRoundTripsTheVisibleMonth() {
        val today = YearMonth.of(2026, 10)
        val page = CalendarPager.pageOf(today, today)
        assertEquals(today, CalendarPager.monthOf(page))
        assertTrue(page < CalendarPager.pageCount(today))
    }

    @Test
    fun merchantKeyIgnoresCaseAndSpacing() {
        assertEquals("starbucks", MerchantKey.normalize("  StarBucks\u3000 "))
        assertEquals("美团", MerchantKey.normalize(" 美团 "))
        assertEquals("美团", MerchantKey.display(" 美团 "))
        assertEquals("", MerchantKey.normalize("   "))
    }

    @Test
    fun rememberedCategoryBeatsKeywordsAndArchivedMappingFallsBack() {
        val categories = listOf(
            category("food", "餐饮", archived = false),
            category("shop", "购物", archived = false),
            category("old", "交通", archived = true),
            category("other", "其他", archived = false),
        )
        assertEquals(
            "shop",
            CategoryGuess.idFor(TransactionType.EXPENSE, "美团外卖", categories, mappedCategoryId = "shop"),
        )
        assertEquals(
            "food",
            CategoryGuess.idFor(TransactionType.EXPENSE, "美团外卖", categories, mappedCategoryId = "old"),
        )
        assertEquals(
            "food",
            CategoryGuess.idFor(TransactionType.EXPENSE, "美团外卖", categories, mappedCategoryId = null),
        )
    }

    @Test
    fun memoryPolicyRequiresARealCategoryChangeAndAMerchant() {
        assertFalse(
            MerchantMemoryPolicy.shouldRemember(
                TransactionType.EXPENSE, "美团", "food", "food", categoryChanged = false,
            ),
        )
        assertTrue(
            MerchantMemoryPolicy.shouldRemember(
                TransactionType.EXPENSE, "美团", "shop", "food", categoryChanged = true,
            ),
        )
        assertFalse(
            MerchantMemoryPolicy.shouldRemember(
                TransactionType.EXPENSE, "  ", "shop", "food", categoryChanged = true,
            ),
        )
        assertFalse(
            MerchantMemoryPolicy.shouldRemember(
                TransactionType.TRANSFER, "美团", null, null, categoryChanged = true,
            ),
        )
    }

    @Test
    fun widgetMasksOnlyWhenLockIsOnAndTheSettingAllowsIt() {
        assertTrue(WidgetPrivacy.maskAmounts(lockConfigured = true, maskWhenLocked = true))
        assertFalse(WidgetPrivacy.maskAmounts(lockConfigured = true, maskWhenLocked = false))
        assertFalse(WidgetPrivacy.maskAmounts(lockConfigured = false, maskWhenLocked = true))
    }

    private fun category(id: String, name: String, archived: Boolean) = Category(
        id = id,
        ledgerId = "ledger",
        name = name,
        type = CategoryType.EXPENSE,
        iconKey = "other",
        isArchived = archived,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
