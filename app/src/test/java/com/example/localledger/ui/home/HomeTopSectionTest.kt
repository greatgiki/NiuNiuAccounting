package com.example.localledger.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.ui.theme.LocalLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeTopSectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun monthAndBalanceLeadAndTheSwitcherMovesAMonth() {
        var month by mutableStateOf(YearMonth.of(2026, 10))
        var ledgerClicks = 0
        compose.setContent {
            HomeTopFrame(
                ledgerName = "日常账本",
                yearMonth = month,
                incomeMinor = 1_280_000,
                expenseMinor = 43_650,
                onLedgerClick = { ledgerClicks += 1 },
                onPreviousMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
            )
        }
        compose.onNodeWithText("日常账本").assertIsDisplayed()
        compose.onNodeWithText("2026年10月").assertIsDisplayed()
        compose.onNodeWithText("本月结余").assertIsDisplayed()
        compose.onNodeWithText("¥12363.50").assertIsDisplayed()
        compose.onNodeWithText("¥12800.00").assertIsDisplayed()
        compose.onNodeWithText("¥436.50").assertIsDisplayed()
        compose.onNodeWithText("收入").assertIsDisplayed()
        compose.onNodeWithText("支出").assertIsDisplayed()
        compose.onNodeWithText("日常账本").performClick()
        assertEquals(1, ledgerClicks)
        compose.onNodeWithContentDescription("上一月").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2026年9月").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一月").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2026年10月").assertIsDisplayed()
    }

    @Test
    fun emptyMonthKeepsTheFiguresAndExplainsThem() {
        compose.setContent {
            HomeTopFrame(
                ledgerName = "",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 0,
                expenseMinor = 0,
            )
        }
        compose.onNodeWithText("选择账本").assertIsDisplayed()
        compose.onAllNodesWithText("¥0.00").assertCountEquals(3)
        compose.onNodeWithText("本月还没有收支").assertIsDisplayed()
    }

    @Test
    fun longBalanceStaysOneLine() {
        compose.setContent {
            HomeTopFrame(
                ledgerName = "旅行基金账本名称很长需要收起但是仍然应当留在一行里不要把月份挤乱",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 12_345_678_900,
                expenseMinor = 50,
            )
        }
        compose.onNodeWithText("¥123456789.00").assertIsDisplayed()
        compose.onNodeWithText("旅行基金账本名称很长需要收起但是仍然应当留在一行里不要把月份挤乱").assertIsDisplayed()
        val ledger = compose.onNodeWithTag("home-ledger").getUnclippedBoundsInRoot()
        assertTrue("ledger should stay inside the page, right=${ledger.right}", ledger.right <= 348.dp)
    }

    @Test
    fun ledgerClearsTheStatusBarInset() {
        compose.setContent {
            HomeTopFrame(
                ledgerName = "日常账本",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 1_280_000,
                expenseMinor = 43_650,
                topInset = 24.dp,
            )
        }
        val top = compose.onNodeWithTag("home-ledger").getUnclippedBoundsInRoot().top
        assertEquals(40f, top.value, 1.5f)
    }

    @Test
    fun monthSwitcherSitsWithTheLabelInsteadOfTheScreenEdges() {
        compose.setContent {
            HomeTopFrame(
                ledgerName = "日常账本",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 1_280_000,
                expenseMinor = 43_650,
            )
        }
        val previous = compose.onNodeWithContentDescription("上一月").getUnclippedBoundsInRoot()
        val month = compose.onNodeWithTag("home-month").getUnclippedBoundsInRoot()
        val next = compose.onNodeWithContentDescription("下一月").getUnclippedBoundsInRoot()
        assertTrue("previous month should leave the screen edge, left=${previous.left}", previous.left > 24.dp)
        assertTrue(
            "month label should sit against the previous control, gap=${month.left - previous.right}",
            month.left - previous.right < 32.dp,
        )
        assertTrue(
            "month label should sit against the next control, gap=${next.left - month.right}",
            next.left - month.right < 32.dp,
        )
    }

    @Test
    fun captureHomeTop() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var income by mutableLongStateOf(1_280_000L)
        var expense by mutableLongStateOf(43_650L)
        var inset by mutableStateOf(0.dp)
        compose.setContent {
            HomeTopFrame(
                mode = mode,
                ledgerName = "日常账本",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = income,
                expenseMinor = expense,
                topInset = inset,
            )
        }
        capture("home_top_after_light")
        mode = ThemeMode.DARK
        capture("home_top_after_dark")
        mode = ThemeMode.LIGHT
        income = 0L
        expense = 0L
        capture("home_top_after_empty_light")
        mode = ThemeMode.DARK
        capture("home_top_after_empty_dark")
        mode = ThemeMode.LIGHT
        income = 12_345_678_900L
        expense = 50L
        inset = 0.dp
        capture("home_top_after_long_light")
        income = 1_280_000L
        expense = 43_650L
        inset = 24.dp
        capture("home_top_after_inset_light")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithTag("shot").fetchSemanticsNode()
        val rootView = (node.root as ViewRootForTest).view
        val full = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        rootView.draw(Canvas(full))
        val rect = node.boundsInRoot
        val left = rect.left.toInt().coerceIn(0, full.width - 1)
        val top = rect.top.toInt().coerceIn(0, full.height - 1)
        val width = rect.width.toInt().coerceIn(1, full.width - left)
        val height = rect.height.toInt().coerceIn(1, full.height - top)
        val bitmap = Bitmap.createBitmap(full, left, top, width, height)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        full.recycle()
    }
}

@Composable
private fun HomeTopFrame(
    ledgerName: String,
    yearMonth: YearMonth,
    incomeMinor: Long,
    expenseMinor: Long,
    mode: ThemeMode = ThemeMode.LIGHT,
    topInset: Dp = 0.dp,
    onLedgerClick: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    LocalLedgerTheme(themeMode = mode) {
        Column(
            modifier = Modifier
                .testTag("shot")
                .width(360.dp)
                .background(MaterialTheme.colorScheme.background)
                .homeSafeInsets(WindowInsets(top = topInset))
                .padding(HomeListPadding),
        ) {
            HomeTopSection(
                ledgerName = ledgerName,
                yearMonth = yearMonth,
                incomeMinor = incomeMinor,
                expenseMinor = expenseMinor,
                currencyCode = "CNY",
                onLedgerClick = onLedgerClick,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }
    }
}
