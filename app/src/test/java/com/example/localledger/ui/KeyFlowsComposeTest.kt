package com.example.localledger.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.budget.BudgetEditDialog
import com.example.localledger.ui.budget.BudgetEditor
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.TransactionAmountText
import com.example.localledger.ui.editor.AmountKeypad
import com.example.localledger.ui.home.BudgetProgressRow
import com.example.localledger.ui.home.HomeBudgetLine
import com.example.localledger.ui.recurring.DeleteRuleDialog
import com.example.localledger.ui.recurring.RecurringUiState
import com.example.localledger.ui.recurring.ResumeRuleDialog
import com.example.localledger.ui.recurring.RuleDialog
import com.example.localledger.ui.settings.RevalueConfirmDialog
import com.example.localledger.ui.statistics.ExpenseCategoryBars
import com.example.localledger.ui.theme.LocalLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
