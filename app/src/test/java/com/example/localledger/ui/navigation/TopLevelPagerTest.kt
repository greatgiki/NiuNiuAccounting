package com.example.localledger.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.component.SwipeToDeleteRow
import com.example.localledger.ui.editor.AccountSnapshot
import com.example.localledger.ui.editor.CategorySnapshot
import com.example.localledger.ui.editor.EditorInput
import com.example.localledger.ui.editor.assembleEditorUiState
import com.example.localledger.ui.editor.categoryRows
import com.example.localledger.ui.theme.LocalLedgerTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TopLevelPagerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun topLevelPagesFollowBottomBarOrderAndExcludeTheCreateAction() {
        assertEquals(
            listOf("首页", "明细", "统计", "设置"),
            TopLevelPage.entries.map { it.label },
        )
        assertEquals(0, TopLevelPage.Home.ordinal)
        assertEquals(1, TopLevelPage.Transactions.ordinal)
        assertEquals(2, TopLevelPage.Statistics.ordinal)
        assertEquals(3, TopLevelPage.Settings.ordinal)
        assertEquals(TopLevelPage.Statistics, TopLevelPage.fromIndex(2))
        assertTrue(TopLevelPage.entries.none { it.label == "记一笔" })
        assertTrue(TAB_SWITCH_MILLIS in 250..300)
        assertTrue(isFilteredTransactionRoute(Routes.TRANSACTIONS_PATTERN))
        assertFalse(isFilteredTransactionRoute(Routes.MAIN))
        assertFalse(isFilteredTransactionRoute(null))
    }

    @Test
    fun pageFadeFollowsDistanceFromTheSettledPage() {
        assertEquals(1f, pageFadeAlpha(0, 0f, 0), 0.001f)
        assertEquals(0.86f, pageFadeAlpha(0, 0.5f, 0), 0.001f)
        assertEquals(0.72f, pageFadeAlpha(0, 0f, 1), 0.001f)
    }

    @Test
    fun gestureGateDisablesPagerScrollUntilEveryClaimIsReleased() {
        val gate = PagerGestureGate()
        assertTrue(gate.userScrollEnabled)
        gate.claim()
        gate.claim()
        assertFalse(gate.userScrollEnabled)
        gate.release()
        assertFalse(gate.userScrollEnabled)
        gate.release()
        assertTrue(gate.userScrollEnabled)
        gate.release()
        assertTrue(gate.userScrollEnabled)
    }

    @Test
    fun tappingATabSelectsThatPageAndTheCenterButtonStaysAnAction() {
        var selected = TopLevelPage.Home
        var created = false
        compose.setContent {
            LocalLedgerTheme {
                LedgerBottomBar(
                    selectedPage = selected,
                    onSelect = { selected = it },
                    onCreate = { created = true },
                )
            }
        }
        compose.onNodeWithText("首页").assertIsSelected()
        compose.onNodeWithText("统计").performClick()
        assertEquals(TopLevelPage.Statistics, selected)
        assertEquals(2, selected.ordinal)
        compose.onNodeWithText("记一笔").performClick()
        assertTrue(created)
        assertEquals(TopLevelPage.Statistics, selected)
    }

    @Test
    fun bottomBarTapScrollsThePagerToTheSameIndex() {
        val holder = arrayOfNulls<PagerState>(1)
        compose.setContent {
            LocalLedgerTheme {
                val pagerState = rememberPagerState(pageCount = { TopLevelPage.entries.size })
                holder[0] = pagerState
                val scope = rememberCoroutineScope()
                Column(Modifier.width(400.dp).height(320.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(180.dp).testTag("pager"),
                        beyondViewportPageCount = 1,
                    ) { index ->
                        Box(Modifier.fillMaxSize().testTag("page-$index")) {
                            Text("内容-${TopLevelPage.entries[index].label}")
                        }
                    }
                    LedgerBottomBar(
                        selectedPage = TopLevelPage.fromIndex(pagerState.targetPage),
                        onSelect = { target ->
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    target.ordinal,
                                    animationSpec = tabSwitchTween(),
                                )
                            }
                        },
                        onCreate = {},
                    )
                }
            }
        }
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        assertEquals(TopLevelPage.Settings.ordinal, holder[0]!!.currentPage)
        compose.onNodeWithTag("page-3").assertExists()
        compose.onNodeWithText("设置").assertIsSelected()
    }

    @Test
    fun swipeToDeleteInsideThePagerDoesNotChangePage() {
        val holder = arrayOfNulls<PagerState>(1)
        var requested = 0
        compose.setContent {
            LocalLedgerTheme {
                val pagerState = rememberPagerState(pageCount = { TopLevelPage.entries.size })
                holder[0] = pagerState
                val gate = androidx.compose.runtime.remember { PagerGestureGate() }
                CompositionLocalProvider(LocalPagerGestureGate provides gate) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.width(400.dp).height(160.dp).testTag("pager"),
                        userScrollEnabled = gate.userScrollEnabled,
                        beyondViewportPageCount = 1,
                    ) { index ->
                        if (index == 0) {
                            SwipeToDeleteRow(
                                onDeleteRequest = { requested += 1 },
                                modifier = Modifier.fillMaxWidth().testTag("txn-row"),
                            ) {
                                Text("午餐")
                            }
                        } else {
                            Text("page$index", modifier = Modifier.testTag("page-$index"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("txn-row").performTouchInput {
            swipeLeft(startX = width * 0.95f, endX = width * 0.05f, durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals(1, requested)
        compose.onNodeWithText("午餐").assertExists()
        assertEquals(0, holder[0]!!.currentPage)
    }

    @Test
    fun newTransactionShellIsReadyBeforeCategoriesLoad() {
        val state = assembleEditorUiState(
            isEdit = false,
            ledgerId = "ledger",
            lastAccountId = null,
            input = EditorInput(type = TransactionType.EXPENSE, initialized = true),
            accounts = AccountSnapshot.EMPTY,
            categories = CategorySnapshot.EMPTY,
        )
        assertFalse(state.isLoading)
        assertFalse(state.isEdit)
        assertFalse(state.categoriesLoaded)
        assertFalse(state.canSave)
    }

    @Test
    fun categoryRowsStayInFoursAndEnableSaveOnceRequiredFieldsExist() {
        val categories = List(9) { index ->
            Category(
                id = "c$index",
                ledgerId = "ledger",
                name = "分类$index",
                type = CategoryType.EXPENSE,
                iconKey = "restaurant",
                createdAt = 0,
                updatedAt = 0,
            )
        }
        val rows = categoryRows(categories)
        assertEquals(listOf(4, 4, 1), rows.map { it.size })
        val accounts = AccountSnapshot(
            all = emptyList(),
            active = emptyList(),
            loaded = true,
        )
        val blocked = assembleEditorUiState(
            isEdit = false,
            ledgerId = "ledger",
            lastAccountId = null,
            input = EditorInput(type = TransactionType.EXPENSE, amountText = "12", initialized = true),
            accounts = accounts,
            categories = CategorySnapshot(
                all = categories,
                active = categories,
                rows = rows,
                loaded = true,
            ),
        )
        assertFalse(blocked.canSave)
        assertEquals(rows, blocked.categoryRows)
        val cash = Account(
            id = "cash",
            ledgerId = "ledger",
            name = "现金",
            type = AccountType.CASH,
            createdAt = 0,
            updatedAt = 0,
        )
        val ready = assembleEditorUiState(
            isEdit = false,
            ledgerId = "ledger",
            lastAccountId = "cash",
            input = EditorInput(
                type = TransactionType.EXPENSE,
                amountText = "12",
                categoryId = "c0",
                initialized = true,
            ),
            accounts = AccountSnapshot(listOf(cash), listOf(cash), loaded = true),
            categories = CategorySnapshot(categories, categories, rows, loaded = true),
        )
        assertTrue(ready.canSave)
        assertEquals("cash", ready.input.accountId)
        assertFalse(ready.isLoading)
    }
}
