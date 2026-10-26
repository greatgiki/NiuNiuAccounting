package com.example.localledger.ui.platform

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
import android.view.View.OVER_SCROLL_NEVER
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.R
import com.example.localledger.ui.theme.LocalLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge pulls must not move content. Compose overscroll is off for every
 * scrollable under the theme, and the window tree opts out of the view-level
 * effect HyperOS applies on top of that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoOverscrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeLeavesListsColumnsAndPagerWithoutOverscroll() {
        var factoryPresent = false
        var effectCreated = false
        compose.setContent {
            LocalLedgerTheme {
                factoryPresent = LocalOverscrollFactory.current != null
                effectCreated = rememberOverscrollEffect() != null
                LazyColumn(Modifier.height(80.dp)) {
                    items(listOf("a", "b"), key = { it }, contentType = { "row" }) { row ->
                        Text(row)
                    }
                }
                Column(Modifier.height(60.dp).verticalScroll(rememberScrollState())) {
                    Text("设置")
                }
                HorizontalPager(state = rememberPagerState { 2 }, modifier = Modifier.height(40.dp)) {
                    Text("页")
                }
            }
        }
        compose.waitForIdle()
        assertEquals(false, factoryPresent)
        assertEquals(false, effectCreated)
    }

    @Test
    fun viewTreeOverscrollIsNever() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val root = FrameLayout(context)
        val child = ScrollView(context)
        root.addView(child)
        assertEquals(OVER_SCROLL_IF_CONTENT_SCROLLS, root.overScrollMode)
        assertEquals(OVER_SCROLL_IF_CONTENT_SCROLLS, child.overScrollMode)
        applyNeverOverscroll(root)
        assertEquals(OVER_SCROLL_NEVER, root.overScrollMode)
        assertEquals(OVER_SCROLL_NEVER, child.overScrollMode)
    }

    @Test
    fun programmaticViewsKeepThePlatformDefaultUntilTheTreeIsUpdated() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val themed = ContextThemeWrapper(base, R.style.Theme_LocalLedger)
        val view = View(themed)
        // View() does not consult android:overScrollMode, so the theme item
        // alone leaves a ComposeView on the platform default.
        assertEquals(OVER_SCROLL_IF_CONTENT_SCROLLS, view.overScrollMode)
        applyNeverOverscroll(view)
        assertEquals(OVER_SCROLL_NEVER, view.overScrollMode)
    }

    @Test
    fun windowDecorContentAndComposeViewAreNever() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        disableWindowOverscroll(activity.window)
        val decor = activity.window.decorView
        val content = activity.findViewById<View>(android.R.id.content)
        assertEquals(OVER_SCROLL_NEVER, decor.overScrollMode)
        assertEquals(OVER_SCROLL_NEVER, content.overScrollMode)
        assertEquals(OVER_SCROLL_NEVER, composeView.overScrollMode)
        for (index in 0 until composeView.childCount) {
            assertEquals(OVER_SCROLL_NEVER, composeView.getChildAt(index).overScrollMode)
        }
    }
}
