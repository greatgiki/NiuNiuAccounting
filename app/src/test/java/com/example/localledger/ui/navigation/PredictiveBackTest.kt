package com.example.localledger.ui.navigation

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
class PredictiveBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewStaysBelowAFullPageAndFollowsTheSwipeEdge() {
        assertTrue(PREDICTIVE_BACK_TRAVEL > 0f)
        assertTrue(PREDICTIVE_BACK_TRAVEL < 0.35f)
        assertTrue(NAV_SLIDE_FRACTION in 0.08f..0.3f)
        assertEquals(0f, predictiveTravelFraction(0f), 0.0001f)
        assertEquals(PREDICTIVE_BACK_TRAVEL, predictiveTravelFraction(1f), 0.0001f)
        assertEquals(PREDICTIVE_BACK_TRAVEL / 2f, predictiveTravelFraction(0.5f), 0.0001f)
        assertTrue(predictiveBackOffsetPx(200f, 1f, fromRight = false) > 0f)
        assertTrue(predictiveBackOffsetPx(200f, 1f, fromRight = true) < 0f)
        assertEquals(0f, predictiveBackOffsetPx(200f, 0f, fromRight = false), 0.0001f)
    }

    @Test
    fun progressDoesNotCommitAndCancelClearsThePreview() {
        val dragging = PredictiveBackGesture()
        dragging.onProgress(0.4f, BackEventCompat.EDGE_LEFT)
        assertEquals(PredictiveBackGesture.Phase.Dragging, dragging.phase)
        assertFalse(dragging.committed)
        assertEquals(0.4f, dragging.progress, 0.0001f)
        dragging.onCancelled()
        assertEquals(PredictiveBackGesture.Phase.Cancelled, dragging.phase)
        assertEquals(0f, dragging.progress, 0.0001f)
        assertFalse(dragging.committed)

        val committed = PredictiveBackGesture()
        committed.onProgress(0.2f, BackEventCompat.EDGE_RIGHT)
        assertTrue(committed.onCompleted())
        assertFalse(committed.onCompleted())
        committed.onProgress(0.9f, BackEventCompat.EDGE_LEFT)
        committed.onCancelled()
        assertTrue(committed.committed)
        assertEquals(PredictiveBackGesture.Phase.Committed, committed.phase)
        assertEquals(0.2f, committed.progress, 0.0001f)
        assertEquals(BackEventCompat.EDGE_RIGHT, committed.swipeEdge)

        val button = PredictiveBackGesture()
        assertTrue(button.onCompleted())
        assertEquals(PredictiveBackGesture.Phase.Committed, button.phase)
    }

    @Test
    fun dragDoesNotRevealThePreviousPageUntilTheGestureIsReleased() {
        compose.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") { Text("home-screen") }
                composable("child") { entry ->
                    PredictiveBackFrame(navController, entry) { Text("child-screen") }
                }
            }
            LaunchedEffect(Unit) { navController.navigate("child") }
        }
        compose.waitUntil(timeoutMillis = 5_000) { settledOn("child-screen") }

        val dispatcher = compose.activity.onBackPressedDispatcher
        val event = BackEventCompat(0f, 0f, 0.65f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        compose.waitForIdle()
        assertShown("child-screen")
        assertAbsent("home-screen")

        dispatcher.dispatchOnBackCancelled()
        compose.waitForIdle()
        assertShown("child-screen")
        assertAbsent("home-screen")

        dispatcher.onBackPressed()
        compose.waitForIdle()
        assertShown("home-screen")
        assertAbsent("child-screen")
    }

    @Test
    fun nestedBackHandlerRunsOnReleaseAndDoesNotPopTheScreen() {
        var steps = 0
        compose.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") { Text("home-screen") }
                composable("child") { entry ->
                    PredictiveBackFrame(navController, entry) {
                        var inner by remember { mutableStateOf(true) }
                        BackHandler(enabled = inner) {
                            inner = false
                            steps += 1
                        }
                        Text(if (inner) "inner-step" else "inner-done")
                    }
                }
            }
            LaunchedEffect(Unit) { navController.navigate("child") }
        }
        compose.waitUntil(timeoutMillis = 5_000) { settledOn("inner-step") }

        val dispatcher = compose.activity.onBackPressedDispatcher
        val event = BackEventCompat(0f, 0f, 0.4f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        compose.waitForIdle()
        assertEquals(0, steps)
        assertShown("inner-step")
        assertAbsent("home-screen")

        dispatcher.dispatchOnBackCancelled()
        compose.waitForIdle()
        assertEquals(0, steps)
        assertShown("inner-step")

        dispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(1, steps)
        assertShown("inner-done")
        assertAbsent("home-screen")

        dispatcher.onBackPressed()
        compose.waitForIdle()
        assertShown("home-screen")
        assertAbsent("inner-done")
    }

    private fun assertShown(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        compose.onAllNodesWithText(text).assertCountEquals(0)
    }

    private fun settledOn(label: String): Boolean {
        val shown = compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        val home = compose.onAllNodesWithText("home-screen").fetchSemanticsNodes()
        val homeGone = label == "home-screen" || home.isEmpty()
        return shown && homeGone
    }
}
