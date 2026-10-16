package com.example.localledger.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.budget.BudgetEditDialog
import com.example.localledger.ui.budget.BudgetEditor
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.component.CollapsibleSectionHeader
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.SubmitGate
import com.example.localledger.ui.component.SwipeToDeleteRow
import com.example.localledger.ui.component.TransactionAmountText
import com.example.localledger.ui.detail.windowedPage
import com.example.localledger.ui.editor.AmountKeypad
import com.example.localledger.ui.home.BudgetProgressRow
import com.example.localledger.ui.home.HomeBudgetLine
import com.example.localledger.ui.navigation.LedgerBottomBar
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.recurring.DeleteRuleDialog
import com.example.localledger.ui.recurring.RecurringUiState
import com.example.localledger.ui.recurring.ResumeRuleDialog
import com.example.localledger.ui.recurring.RuleDialog
import com.example.localledger.ui.settings.RevalueConfirmDialog
import com.example.localledger.ui.statistics.ExpenseCategoryBars
import com.example.localledger.ui.statistics.ExpenseShareChart
import com.example.localledger.ui.theme.ExpenseColorDark
import com.example.localledger.ui.theme.ExpenseColorLight
import com.example.localledger.ui.theme.IncomeColorDark
import com.example.localledger.ui.theme.IncomeColorLight
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.ui.theme.amountColorFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KeyFlowsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun currencySymbolsFollowTheAccountOrLedgerCurrency() {
        compose.setContent {
            LocalLedgerTheme {
                MoneyText(amountMinor = 1_234, currencyCode = "USD")
                MoneyText(amountMinor = 1_500, currencyCode = "JPY")
                TransactionAmountText(
                    type = TransactionType.EXPENSE,
                    amountMinor = 2_500,
                    currencyCode = "CNY",
                )
            }
        }
        compose.onNodeWithText("$12.34").assertIsDisplayed()
        compose.onNodeWithText("円1500").assertIsDisplayed()
        compose.onNodeWithText("-¥25.00").assertIsDisplayed()
    }

    @Test
    fun budgetProgressShowsRemainingAndOverspend() {
        compose.setContent {
            LocalLedgerTheme {
                BudgetProgressRow(
                    line = HomeBudgetLine("food", "餐饮", 10_000, 5_000, 5_000, "50%", false),
                    currencyCode = "CNY",
                )
                BudgetProgressRow(
                    line = HomeBudgetLine("taxi", "交通", 10_000, 12_000, -2_000, "120%", true),
                    currencyCode = "CNY",
                )
            }
        }
        compose.onNodeWithText("剩余 ¥50.00 / ¥100.00").assertIsDisplayed()
        compose.onNodeWithText("超支 ¥20.00").assertIsDisplayed()
    }

    @Test
    fun budgetCreateDialogSavesATotalBudget() {
        var categoryId: String? = "unset"
        var amount = ""
        compose.setContent {
            LocalLedgerTheme {
                BudgetEditDialog(
                    target = BudgetEditor(existing = null, categoryId = null),
                    categories = listOf(foodCategory()),
                    currencyCode = "USD",
                    onDismiss = {},
                    onConfirm = { id, text ->
                        categoryId = id
                        amount = text
                    },
                )
            }
        }
        compose.onNodeWithText("新建预算").assertIsDisplayed()
        compose.onNodeWithText("预算金额").performTextInput("20")
        compose.onNodeWithText("保存").performClick()
        assertNull(categoryId)
        assertEquals("20", amount)
    }

    @Test
    fun amountKeypadReportsDigitsDecimalAndClear() {
        val keys = mutableListOf<String>()
        compose.setContent {
            LocalLedgerTheme {
                AmountKeypad(onKey = { keys += it })
            }
        }
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText(".").performClick()
        compose.onNodeWithText("5").performClick()
        compose.onNodeWithText("清空").performClick()
        assertEquals(listOf("1", ".", "5", "clear"), keys)
    }

    @Test
    fun recurringRuleDialogSavesTheEnteredAmount() {
        var amount = ""
        compose.setContent {
            LocalLedgerTheme {
                RuleDialog(
                    state = RecurringUiState(
                        isLoading = false,
                        accounts = listOf(cashAccount()),
                        expenseCategories = listOf(foodCategory()),
                    ),
                    existing = null,
                    onDismiss = {},
                    onSave = { amount = it.amountText },
                )
            }
        }
        compose.onNodeWithText("新建周期规则").assertIsDisplayed()
        compose.onNodeWithText("金额").performTextInput("12.5")
        compose.onNodeWithText("保存").performClick()
        assertEquals("12.5", amount)
    }

    @Test
    fun statisticsCategoryClickReportsTheCategoryId() {
        var clicked: String? = null
        compose.setContent {
            LocalLedgerTheme {
                ExpenseCategoryBars(
                    categories = listOf(CategoryTotal("food", "餐饮", "restaurant", 2_500)),
                    totalMinor = 2_500,
                    currencyCode = "EUR",
                    onCategoryClick = { clicked = it },
                )
            }
        }
        compose.onNodeWithText("€25.00").assertIsDisplayed()
        compose.onNodeWithText("餐饮").performClick()
        assertEquals("food", clicked)
    }

    @Test
    fun resumeDialogCanBackfill() {
        var action = ""
        compose.setContent {
            LocalLedgerTheme {
                ResumeRuleDialog(
                    onBackfill = { action = "backfill" },
                    onSkip = { action = "skip" },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("补记错过的日期").performClick()
        assertEquals("backfill", action)
    }

    @Test
    fun resumeDialogCanSkipToTheNextDate() {
        var action = ""
        compose.setContent {
            LocalLedgerTheme {
                ResumeRuleDialog(
                    onBackfill = {},
                    onSkip = { action = "skip" },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("跳到下一次").performClick()
        assertEquals("skip", action)
    }

    @Test
    fun deleteDialogCanKeepGeneratedTransactions() {
        var action = ""
        compose.setContent {
            LocalLedgerTheme {
                DeleteRuleDialog(
                    onKeepTransactions = { action = "keep" },
                    onDeleteTransactions = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("只删除规则").performClick()
        assertEquals("keep", action)
    }

    @Test
    fun deleteDialogCanRemoveGeneratedTransactions() {
        var action = ""
        compose.setContent {
            LocalLedgerTheme {
                DeleteRuleDialog(
                    onKeepTransactions = {},
                    onDeleteTransactions = { action = "drop" },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("同时删除已生成流水").performClick()
        assertEquals("drop", action)
    }

    @Test
    fun revalueConfirmationCanBeDismissed() {
        var confirmed = false
        compose.setContent {
            LocalLedgerTheme {
                RevalueConfirmDialog(
                    onConfirm = { confirmed = true },
                    onDismiss = { confirmed = false },
                )
            }
        }
        compose.onNodeWithText("按当前汇率重算？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(false, confirmed)
    }

    @Test
    fun revalueConfirmationRunsOnlyAfterConfirm() {
        var confirmed = false
        compose.setContent {
            LocalLedgerTheme {
                RevalueConfirmDialog(
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("重算").performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun amountColorsFollowTheAppThemeMode() {
        assertEquals(ExpenseColorDark, amountColorFor(dark = true, TransactionType.EXPENSE))
        assertEquals(ExpenseColorLight, amountColorFor(dark = false, TransactionType.EXPENSE))
        assertEquals(IncomeColorDark, amountColorFor(dark = true, TransactionType.INCOME))
        assertEquals(IncomeColorLight, amountColorFor(dark = false, TransactionType.INCOME))
        compose.setContent {
            LocalLedgerTheme(themeMode = ThemeMode.DARK) {
                Text(if (LocalLedgerDarkTheme.current) "应用深色" else "应用浅色")
            }
        }
        compose.onNodeWithText("应用深色").assertIsDisplayed()
    }

    @Test
    fun confirmButtonUsesTheActionName() {
        var confirmed = ""
        compose.setContent {
            LocalLedgerTheme {
                ConfirmDeleteDialog(
                    title = "停用账户“现金”？",
                    message = "停用后不能再用于新流水，历史流水和余额仍会保留。",
                    confirmLabel = "停用",
                    onConfirm = { confirmed = "停用" },
                    onDismiss = {},
                )
            }
        }
        compose.onAllNodesWithText("删除").assertCountEquals(0)
        compose.onNodeWithText("停用").performClick()
        assertEquals("停用", confirmed)
    }

    @Test
    fun archiveConfirmUsesArchiveLabel() {
        compose.setContent {
            LocalLedgerTheme {
                ConfirmDeleteDialog(
                    title = "归档账本“日常”？",
                    message = "归档后不能再记录新流水，历史数据仍会保留。",
                    confirmLabel = "归档",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("归档").assertIsDisplayed()
        compose.onAllNodesWithText("删除").assertCountEquals(0)
    }

    @Test
    fun submittingConfirmCannotBeTappedAgain() {
        var clicks = 0
        compose.setContent {
            LocalLedgerTheme {
                ConfirmDeleteDialog(
                    title = "删除这条记录？",
                    message = "删除后无法恢复，相关账户余额和统计会立即更新。",
                    submitting = true,
                    onConfirm = { clicks += 1 },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("处理中…").assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun budgetDialogStaysOpenAndBlocksAnotherSave() {
        var submits = 0
        var submitting by mutableStateOf(false)
        compose.setContent {
            LocalLedgerTheme {
                BudgetEditDialog(
                    target = BudgetEditor(existing = null, categoryId = null),
                    categories = listOf(foodCategory()),
                    currencyCode = "CNY",
                    submitting = submitting,
                    errorMessage = if (submitting) null else null,
                    onDismiss = {},
                    onConfirm = { _, _ ->
                        submits += 1
                        submitting = true
                    },
                )
            }
        }
        compose.onNodeWithText("预算金额").performTextInput("20")
        compose.onNodeWithText("保存").performClick()
        assertEquals(1, submits)
        compose.onNodeWithText("新建预算").assertIsDisplayed()
        compose.onNodeWithText("保存中…").assertIsNotEnabled()
    }

    @Test
    fun budgetDialogShowsSaveErrorWithoutClosing() {
        compose.setContent {
            LocalLedgerTheme {
                BudgetEditDialog(
                    target = BudgetEditor(existing = null, categoryId = null),
                    categories = listOf(foodCategory()),
                    currencyCode = "CNY",
                    errorMessage = "预算金额必须大于 0",
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("新建预算").assertIsDisplayed()
        compose.onNodeWithText("预算金额必须大于 0").assertIsDisplayed()
    }

    @Test
    fun disabledSectionStartsCollapsed() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            LocalLedgerTheme {
                CollapsibleSectionHeader(
                    title = "已停用",
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
                if (expanded) Text("现金")
            }
        }
        compose.onAllNodesWithText("现金").assertCountEquals(0)
        compose.onNodeWithText("已停用").performClick()
        compose.onNodeWithText("现金").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起已停用").assertIsDisplayed()
    }

    @Test
    fun centerNavigationOpensNewTransaction() {
        var created = false
        compose.setContent {
            LocalLedgerTheme {
                LedgerBottomBar(
                    currentRoute = Routes.HOME,
                    onNavigate = {},
                    onCreate = { created = true },
                )
            }
        }
        compose.onNodeWithText("明细").assertIsDisplayed()
        compose.onNodeWithText("记一笔").performClick()
        assertTrue(created)
    }

    @Test
    fun swipeAsksToDeleteAndKeepsTheRow() {
        var requested = 0
        compose.setContent {
            LocalLedgerTheme {
                Box(Modifier.width(400.dp)) {
                    SwipeToDeleteRow(
                        onDeleteRequest = { requested += 1 },
                        modifier = Modifier.fillMaxWidth().testTag("txn-row"),
                    ) {
                        Text("午餐")
                    }
                }
            }
        }
        compose.onNodeWithTag("txn-row").performTouchInput {
            swipeLeft(startX = width * 0.95f, endX = width * 0.05f, durationMillis = 400)
        }
        assertEquals(1, requested)
        compose.onNodeWithText("午餐").assertIsDisplayed()
    }

    @Test
    fun narrowShareChartKeepsCategoryNames() {
        compose.setContent {
            LocalLedgerTheme {
                Box(Modifier.width(320.dp)) {
                    ExpenseShareChart(
                        categories = listOf(
                            CategoryTotal("food", "餐饮", "restaurant", 2_500),
                            CategoryTotal("taxi", "交通", "directions_car", 2_500),
                        ),
                        currencyCode = "CNY",
                    )
                }
            }
        }
        compose.onNodeWithText("餐饮").assertIsDisplayed()
        compose.onNodeWithText("交通").assertIsDisplayed()
        compose.onNodeWithContentDescription("支出分类占比，共2项").assertIsDisplayed()
    }

    @Test
    fun longTransactionListsPageBeforeShowingTheRest() {
        val (visible, more) = windowedPage((1..51).toList(), 50)
        assertEquals(50, visible.size)
        assertEquals(1, visible.first())
        assertTrue(more)
        val (all, noMore) = windowedPage(listOf("a", "b"), 50)
        assertEquals(listOf("a", "b"), all)
        assertFalse(noMore)
    }

    @Test
    fun submitGateClosesOnlyAfterSuccessAndRejectsASecondTap() = runBlocking {
        val gate = SubmitGate()
        var runs = 0
        var closed = 0
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val hold = CompletableDeferred<UiOutcome>()
        gate.launch(scope, {
            runs += 1
            hold.await()
        }, { closed += 1 })
        gate.launch(scope, {
            runs += 1
            UiOutcome.Success
        }, { closed += 1 })
        assertEquals(1, runs)
        assertTrue(gate.submitting)
        hold.complete(UiOutcome.Failure("保存失败"))
        assertEquals("保存失败", gate.error)
        assertFalse(gate.submitting)
        assertEquals(0, closed)
        gate.launch(scope, {
            runs += 1
            UiOutcome.Success
        }, { closed += 1 })
        assertEquals(2, runs)
        assertEquals(1, closed)
    }

    private fun foodCategory() = Category(
        id = "food",
        ledgerId = "ledger",
        name = "餐饮",
        type = CategoryType.EXPENSE,
        iconKey = "restaurant",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun cashAccount() = Account(
        id = "cash",
        ledgerId = "ledger",
        name = "现金",
        type = AccountType.CASH,
        createdAt = 0,
        updatedAt = 0,
    )
}
