package com.example.localledger.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.localledger.ui.component.LocalLedgerEntrance
import com.example.localledger.ui.component.rememberLedgerEntrance
import com.example.localledger.ui.theme.LedgerMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * How far a predictive-back drag may move the current screen, as a fraction of its width.
 * Kept well below 1 so the gesture reads as a preview: the page never leaves the screen
 * until the gesture is released and the navigation transition runs.
 */
internal const val PREDICTIVE_BACK_TRAVEL = 0.16f

internal const val PREDICTIVE_BACK_SCALE = 0.03f

internal const val PREDICTIVE_BACK_FADE = 0.05f

/** Shared with the committed navigation slides so preview and pop feel like one motion. */
internal const val NAV_SLIDE_FRACTION = 0.16f

/**
 * Tracks one system back gesture.
 *
 * Progress only updates the preview. [onCompleted] is the sole commit signal, and it is
 * what [androidx.activity.compose.PredictiveBackHandler] emits after the progress flow
 * closes — that is `onBackInvoked`, not `onBackStarted`. [onCancelled] drops the preview
 * and must not navigate.
 */
internal class PredictiveBackGesture {
    var progress: Float = 0f
        private set

    var swipeEdge: Int = BackEventCompat.EDGE_LEFT
        private set

    var phase: Phase = Phase.Idle
        private set

    val committed: Boolean get() = phase == Phase.Committed

    fun onProgress(progress: Float, swipeEdge: Int) {
        if (phase == Phase.Committed) return
        phase = Phase.Dragging
        this.progress = progress.coerceIn(0f, 1f)
        this.swipeEdge = swipeEdge
    }

    /** @return true the first time the gesture is allowed to pop or dismiss. */
    fun onCompleted(): Boolean {
        if (phase == Phase.Committed) return false
        phase = Phase.Committed
        return true
    }

    fun onCancelled() {
        if (phase == Phase.Committed) return
        phase = Phase.Cancelled
        progress = 0f
    }

    enum class Phase {
        Idle,
        Dragging,
        Committed,
        Cancelled,
    }
}

internal fun predictiveTravelFraction(progress: Float): Float =
    PREDICTIVE_BACK_TRAVEL * progress.coerceIn(0f, 1f)

/** Positive translation moves the page to the right (a left-edge back swipe). */
internal fun predictiveBackOffsetPx(widthPx: Float, progress: Float, fromRight: Boolean): Float {
    val direction = if (fromRight) -1f else 1f
    return direction * widthPx * predictiveTravelFraction(progress)
}

internal fun ledgerForwardEnter(): EnterTransition = horizontalEnter(forward = true)

internal fun ledgerForwardExit(): ExitTransition = horizontalExit(forward = true)

internal fun ledgerPopEnter(): EnterTransition = horizontalEnter(forward = false)

internal fun ledgerPopExit(): ExitTransition = horizontalExit(forward = false)

private fun horizontalEnter(forward: Boolean): EnterTransition {
    if (!LedgerMotion.animationsEnabled()) return EnterTransition.None
    val fraction = if (forward) NAV_SLIDE_FRACTION else NAV_SLIDE_FRACTION / 2f
    val direction = if (forward) 1 else -1
    return slideInHorizontally(LedgerMotion.spec(LedgerMotion.Medium)) { width ->
        (width * fraction * direction).toInt()
    } + fadeIn(LedgerMotion.spec(LedgerMotion.Short))
}

private fun horizontalExit(forward: Boolean): ExitTransition {
    if (!LedgerMotion.animationsEnabled()) return ExitTransition.None
    val fraction = if (forward) NAV_SLIDE_FRACTION / 2f else NAV_SLIDE_FRACTION
    val direction = if (forward) -1 else 1
    return slideOutHorizontally(LedgerMotion.spec(LedgerMotion.Medium)) { width ->
        (width * fraction * direction).toInt()
    } + fadeOut(LedgerMotion.spec(LedgerMotion.Short))
}

/**
 * Eats the back gesture before [androidx.navigation.compose.NavHost]'s own handler.
 *
 * NavHost 2.8.x seeks its pop transition from `LaunchedEffect(progress)`. Each progress
 * tick cancels the in-flight [androidx.compose.animation.core.SeekableTransitionState.seekTo]
 * before the transition is pinned, so the full pop animation runs while the finger is still
 * down. Cancelling then snaps that transition back, which is the jump onto the original page.
 * This frame is composed inside the destination, so it is registered after that handler and
 * receives the gesture instead. The back stack changes only after the progress flow completes.
 * Handlers composed inside [content] (a sheet's content, a nested step) are registered later
 * and still win while they are enabled.
 */
@Composable
fun PredictiveBackFrame(
    navController: NavHostController,
    entry: NavBackStackEntry,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val current by navController.currentBackStackEntryAsState()
    val enabled = current?.id == entry.id && navController.previousBackStackEntry != null
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var previewing by remember { mutableStateOf(false) }
    val motion = LedgerMotion.animationsEnabled()
    PredictiveBackHandler(enabled) { events ->
        val gesture = PredictiveBackGesture()
        try {
            events.collect { event ->
                gesture.onProgress(event.progress, event.swipeEdge)
                swipeEdge = event.swipeEdge
                if (motion) {
                    previewing = true
                    progress.snapTo(gesture.progress)
                }
            }
            if (gesture.onCompleted()) {
                // The navigation transition owns the exit. Drop the preview offset first so it
                // is not added on top of the pop slide.
                progress.snapTo(0f)
                previewing = false
                navController.popBackStack()
            }
        } catch (cancelled: CancellationException) {
            gesture.onCancelled()
            withContext(NonCancellable) {
                if (motion && progress.value > 0f) {
                    progress.animateTo(0f, LedgerMotion.settle())
                } else {
                    progress.snapTo(0f)
                }
                previewing = false
            }
        }
    }
    val entrance = rememberLedgerEntrance()
    Box(
        modifier
            .fillMaxSize()
            .then(
                if (previewing) {
                    Modifier.graphicsLayer {
                        val fraction = progress.value
                        if (fraction <= 0f) return@graphicsLayer
                        val fromRight = swipeEdge == BackEventCompat.EDGE_RIGHT
                        translationX = predictiveBackOffsetPx(size.width, fraction, fromRight)
                        val scale = lerp(1f, 1f - PREDICTIVE_BACK_SCALE, fraction)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 1f - PREDICTIVE_BACK_FADE, fraction)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(LocalLedgerEntrance provides entrance) {
            content()
        }
    }
}
