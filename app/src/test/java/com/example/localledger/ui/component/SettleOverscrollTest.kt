package com.example.localledger.ui.component

import android.app.Application
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.localledger.ui.theme.LedgerMotion
import com.example.localledger.ui.theme.LocalLedgerTheme
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composition trace for the edge settle.
 *
 * The platform stretch hitch is a draw-path spike (a new RenderNode plus
 * EdgeEffect), so a JVM test cannot time a GPU frame. Robolectric also does
 * not issue a draw pass. What this trace locks is the contract that removes
 * the spike: fling frames do not engage the effect, the settle never
 * recomposes the rows, the shift stays inside 12dp, and it ends inside one
 * 280ms motion. The shift itself is a canvas translate on the next real frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettleOverscrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun resistedPullApproachesTheCapWithoutReachingIt() {
        assertEquals(0f, SettleOverscroll.resisted(0f, 36f), 0f)
        assertTrue(SettleOverscroll.resisted(-24f, 36f) < 0f)
        var previous = 0f
        var raw = 0f
        while (raw <= 10_000f) {
            val visual = SettleOverscroll.resisted(raw, 36f)
            assertTrue(visual + 0.0001f >= previous)
            assertTrue(visual < 36f)
            previous = visual
            raw += 25f
        }
        val atMax = SettleOverscroll.resisted(36f, 36f)
        assertEquals(36f, SettleOverscroll.rawForVisual(atMax, 36f), 0.02f)
        val nearCap = SettleOverscroll.resisted(3 * 36f, 36f)
        assertEquals(3 * 36f, SettleOverscroll.rawForVisual(nearCap, 36f), 0.05f)
    }

    @Test
    fun settleDurationsFitOneMediumMotion() {
        assertEquals(LedgerMotion.Medium, SettleOverscroll.GiveMillis + SettleOverscroll.ReturnMillis)
        assertEquals(12, SettleOverscroll.MaxVisual.value.toInt())
    }

    @Test
    fun factoriesWithTheSameCapAreEqual() {
        val first = SettleOverscrollFactory(36f)
        val second = SettleOverscrollFactory(36f)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun dragPastTheBoundStaysInsideTheCapAndRelaxesBeforeScrolling() {
        val max = 36f
        val effect = SettleOverscrollEffect(max)
        compose.runOnIdle {
            var calls = 0
            effect.applyToScroll(Offset(0f, 10_000f), NestedScrollSource.UserInput) {
                calls += 1
                Offset.Zero
            }
            assertEquals(1, calls)
            assertTrue(effect.translation.y > max * 0.8f)
            assertTrue(effect.translation.y < max)
            assertTrue(effect.isInProgress)

            effect.applyToScroll(Offset(0f, -10_000f), NestedScrollSource.UserInput) { delta ->
                delta
            }
            assertEquals(0f, effect.translation.y, 0.5f)

            effect.applyToScroll(Offset(0f, 120f), NestedScrollSource.UserInput) { Offset.Zero }
            var passed = 0f
            effect.applyToScroll(Offset(0f, -200f), NestedScrollSource.UserInput) { delta ->
                passed = delta.y
                delta
            }
            assertEquals(-80f, passed, 0.5f)
            assertEquals(0f, effect.translation.y, 0.5f)
            assertTrue(!effect.isInProgress)
        }
    }

    @Test
    fun oppositeEdgeAndHorizontalAxisFollowTheFinger() {
        val effect = SettleOverscrollEffect(36f)
        compose.runOnIdle {
            effect.applyToScroll(Offset(0f, -80f), NestedScrollSource.UserInput) { Offset.Zero }
            assertTrue(effect.translation.y < 0f)
            assertEquals(0f, effect.translation.x, 0f)
            effect.applyToScroll(Offset(40f, 0f), NestedScrollSource.UserInput) { Offset.Zero }
            assertTrue(effect.translation.x > 0f)
            assertEquals(0f, effect.translation.y, 0.5f)
        }
    }

    @Test
    fun flingFramesDoNotEngageTheEffect() {
        val effect = SettleOverscrollEffect(36f)
        compose.runOnIdle {
            val consumed = effect.applyToScroll(Offset(0f, 400f), NestedScrollSource.SideEffect) {
                Offset.Zero
            }
            assertEquals(Offset.Zero, consumed)
            assertEquals(Offset.Zero, effect.translation)
            assertTrue(!effect.isInProgress)
        }
    }

    @Test
    fun removedAnimationsDoNotShiftTheContent() {
        val effect = SettleOverscrollEffect(36f, motionEnabled = { false })
        compose.runOnIdle {
            effect.applyToScroll(Offset(0f, 200f), NestedScrollSource.UserInput) { Offset.Zero }
            assertEquals(Offset.Zero, effect.translation)
        }
    }

    @Test
    fun themeReplacesPlatformOverscroll() {
        var factory: OverscrollFactory? = null
        compose.setContent {
            LocalLedgerTheme {
                factory = LocalOverscrollFactory.current
            }
        }
        compose.waitForIdle()
        assertTrue(factory is SettleOverscrollFactory)
    }

    @Test
    fun slowLeftoverDoesNotLeaveTheBound() {
        runBlocking {
            val effect = SettleOverscrollEffect(36f, motionEnabled = { true })
            effect.applyToFling(Velocity(0f, 50f)) { Velocity.Zero }
            assertEquals(Offset.Zero, effect.translation)
            assertTrue(!effect.isInProgress)
        }
    }

    @Test
    fun lazyListAndColumnBothReceiveTheSettle() {
        val factory = RecordingFactory(48f)
        compose.setContent {
            CompositionLocalProvider(LocalOverscrollFactory provides factory) {
                Column {
                    LazyColumn(Modifier.height(120.dp)) {
                        items(listOf("a", "b", "c"), key = { it }, contentType = { "row" }) { row ->
                            Text(row)
                        }
                    }
                    Column(Modifier.height(80.dp).verticalScroll(rememberScrollState())) {
                        Text("设置")
                        Text("报表")
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2, factory.effects.size)
        assertTrue(factory.effects.all { it.node.node.isAttached })
    }

    @Test
    fun flingIntoTheEdgeSettlesWithoutRecomposingRows() {
        val maxPx = with(compose.density) { SettleOverscroll.MaxVisual.toPx() }
        val factory = RecordingFactory(maxPx)
        val counter = DrawCounter()
        var trigger by mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalOverscrollFactory provides factory) {
                StableRows(Modifier.fillMaxSize(), counter)
                FlingTrigger(trigger, factory)
                PeakProbe(factory, counter)
            }
        }
        compose.waitForIdle()
        val effect = factory.effect
        assertNotNull(effect)
        assertTrue(effect!!.node.node.isAttached)
        val compositionsBefore = counter.compositions
        compose.runOnUiThread { trigger = 1 }
        compose.waitForIdle()
        // Robolectric does not issue a draw pass, so the peak is sampled from
        // the translation state. A real frame only has to translate the canvas.
        assertTrue("peak=${counter.peak} max=$maxPx", counter.peak > 1f)
        assertTrue(counter.peak <= maxPx + 0.5f)
        assertEquals(compositionsBefore, counter.compositions)
        assertEquals(0f, effect.translation.y, 0.5f)
        assertEquals(0f, effect.translation.x, 0.5f)
        assertTrue(!effect.isInProgress)
    }
}

@Stable
private class DrawCounter {
    var compositions: Int = 0
    var peak: Float = 0f
}

private class RecordingFactory(
    private val maxVisualPx: Float,
) : OverscrollFactory {
    val effects = mutableListOf<SettleOverscrollEffect>()

    val effect: SettleOverscrollEffect?
        get() = effects.lastOrNull()

    override fun createOverscrollEffect(): SettleOverscrollEffect =
        SettleOverscrollEffect(maxVisualPx, motionEnabled = { true }).also { effects += it }

    override fun equals(other: Any?): Boolean =
        other is RecordingFactory && other.maxVisualPx == maxVisualPx

    override fun hashCode(): Int = maxVisualPx.hashCode()
}

@Composable
private fun StableRows(modifier: Modifier, counter: DrawCounter) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SideEffect { counter.compositions += 1 }
        repeat(20) { index ->
            Text(
                "行 $index",
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
    }
}

@Composable
private fun FlingTrigger(trigger: Int, factory: RecordingFactory) {
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val effect = factory.effect ?: return@LaunchedEffect
        effect.applyToFling(Velocity(0f, -8_000f)) { Velocity.Zero }
    }
}

@Composable
private fun PeakProbe(factory: RecordingFactory, counter: DrawCounter) {
    LaunchedEffect(factory) {
        snapshotFlow { factory.effect?.translation ?: Offset.Zero }
            .collect { offset ->
                counter.peak = maxOf(counter.peak, abs(offset.y), abs(offset.x))
            }
    }
}
