package com.example.localledger.ui.calendar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.Transaction
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.ui.widget.LedgerWidgetPreview
import com.example.localledger.widget.WidgetSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class CalendarWidgetScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderCalendarAndWidgetPreview() {
        val month = YearMonth.of(2026, 10)
        val selected = LocalDate.of(2026, 10, 21)
        val totals = mapOf(
            month to mapOf(
                LocalDate.of(2026, 10, 3) to IncomeExpenseSummary(0, 3_650),
                LocalDate.of(2026, 10, 12) to IncomeExpenseSummary(128_000, 4_200),
                selected to IncomeExpenseSummary(2_000, 4_800),
            ),
        )
        val rows = listOf(
            detail("lunch", "餐饮", "美团", 3_200, TransactionType.EXPENSE, "restaurant"),
            detail("pay", "工资", "公司", 2_000, TransactionType.INCOME, "salary"),
        )
        var screen by mutableStateOf("calendar")
        compose.setContent {
            LocalLedgerTheme {
                when (screen) {
                    "calendar" -> Box(
                        Modifier
                            .testTag("shot")
                            .width(360.dp)
                            .height(780.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        CalendarContent(
                            visibleMonth = month,
                            months = totals,
                            selected = selected,
                            today = selected,
                            currencyCode = "CNY",
                            dayRows = rows,
                            dayReady = true,
                            onPrevious = {},
                            onNext = {},
                            onSelect = {},
                            onOpen = {},
                        )
                    }
                    "masked" -> Box(
                        Modifier.testTag("shot").width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    ) {
                        LedgerWidgetPreview(
                            snapshot = sampleSnapshot(mask = true),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> Box(
                        Modifier.testTag("shot").width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    ) {
                        LedgerWidgetPreview(
                            snapshot = sampleSnapshot(mask = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("2026年10月").assertExists()
        compose.onNodeWithText("美团", substring = true).assertExists()
        capture("calendar")
        screen = "widget"
        compose.onNodeWithText("记一笔").assertExists()
        compose.onNodeWithText("¥48.00").assertExists()
        capture("widget_preview")
        screen = "masked"
        compose.onAllNodesWithText("••••").assertCountEquals(6)
        capture("widget_preview_masked")
    }

    private fun sampleSnapshot(mask: Boolean) = WidgetSnapshot(
        currencyCode = "CNY",
        today = IncomeExpenseSummary(12_800, 4_800),
        month = IncomeExpenseSummary(128_000, 36_500),
        maskAmounts = mask,
    )

    private fun detail(
        id: String,
        category: String,
        party: String,
        amount: Long,
        type: TransactionType,
        icon: String,
    ) = TransactionDetail(
        transaction = Transaction(
            id = id,
            ledgerId = "ledger",
            type = type,
            amountMinor = amount,
            accountId = "cash",
            toAccountId = null,
            categoryId = category,
            transactionAt = 1_761_000_000_000L,
            note = party,
            createdAt = 1L,
            updatedAt = 1L,
            baseAmountMinor = amount,
            counterparty = party,
        ),
        accountName = "现金",
        toAccountName = null,
        categoryName = category,
        categoryIconKey = icon,
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithTag("shot").fetchSemanticsNode()
        val rootView = (node.root as ViewRootForTest).view
        val bitmap = Bitmap.createBitmap(
            rootView.width.coerceAtLeast(1),
            rootView.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        rootView.draw(Canvas(bitmap))
        listOf(File("build/screenshots"), File("/opt/cursor/artifacts/screenshots")).forEach { dir ->
            dir.mkdirs()
            File(dir, "$name.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }
}
