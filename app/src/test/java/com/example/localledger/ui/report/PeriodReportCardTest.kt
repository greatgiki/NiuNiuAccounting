package com.example.localledger.ui.report

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.example.localledger.domain.model.CategoryFrequency
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.LargestExpense
import com.example.localledger.domain.report.PeriodReportComposer
import com.example.localledger.domain.report.ReportFacts
import com.example.localledger.domain.report.ReportKind
import com.example.localledger.ui.theme.LocalLedgerTheme
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class PeriodReportCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cardShowsTheWeekSummaryTotalsAndHighlight() {
        val report = PeriodReportComposer.compose(
            ReportFacts(
                kind = ReportKind.WEEK,
                ledgerName = "日常账本",
                currencyCode = "CNY",
                start = LocalDate.of(2026, 7, 20),
                endInclusive = LocalDate.of(2026, 7, 26),
                incomeMinor = 20_000,
                expenseMinor = 8_800,
                previousIncomeMinor = 20_000,
                previousExpenseMinor = 10_000,
                expenseCategories = listOf(
                    CategoryTotal("food", "餐饮", "restaurant", 5_000),
                ),
                previousExpenseCategories = listOf(
                    CategoryTotal("food", "餐饮", "restaurant", 6_000),
                ),
                days = listOf(
                    DailyTotal(LocalDate.of(2026, 7, 20), incomeMinor = 20_000, expenseMinor = 0),
                    DailyTotal(LocalDate.of(2026, 7, 21), incomeMinor = 0, expenseMinor = 3_200),
                    DailyTotal(LocalDate.of(2026, 7, 22), incomeMinor = 0, expenseMinor = 1_800),
                    DailyTotal(LocalDate.of(2026, 7, 23), incomeMinor = 0, expenseMinor = 2_000),
                    DailyTotal(LocalDate.of(2026, 7, 24), incomeMinor = 0, expenseMinor = 1_800),
                ),
                largestExpense = LargestExpense("餐饮", 3_200, LocalDate.of(2026, 7, 21), null),
                expenseCounts = listOf(
                    CategoryFrequency("food", "餐饮", "restaurant", 4, 5_000),
                ),
            ),
        )
        compose.setContent {
            LocalLedgerTheme {
                Box(
                    Modifier
                        .testTag("shot")
                        .width(360.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp),
                ) {
                    ReportCard(report)
                }
            }
        }
        compose.onNodeWithText("本周支出比上周少 12%，餐饮仍是最大类别。").assertIsDisplayed()
        compose.onNodeWithText("周报").assertIsDisplayed()
        compose.onNodeWithText("支出较上周少 ¥12.00（12%）").assertIsDisplayed()
        compose.onNodeWithText("最大一笔").assertIsDisplayed()
        compose.onNodeWithText("密码").assertDoesNotExist()
        capture()
    }

    private fun capture() {
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
            File(dir, "weekly_report_card.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }
}
