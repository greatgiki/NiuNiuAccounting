package com.example.localledger.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.localledger.ui.theme.LedgerMotion
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far a scroll may travel past its bound, and how that travel is drawn.
 *
 * Compose 1.8's platform overscroll on API 31+ is a stretch. Mid-fling draws
 * the scrollable directly. The frame a fling hits the top or bottom, stretch
 * starts: every frame of the effect records the whole viewport into a new
 * [android.graphics.RenderNode], runs [android.widget.EdgeEffect] twice (the
 * real stretch and a negation pass, because `RenderNode.clearStretch()` is
 * hidden), then draws the node back. The first of those frames also allocates
 * the buffer. HyperOS replaces `EdgeEffect` with its own stretch shader, so
 * that frame is a visible hitch on every scrollable — the home list, the
 * ledger, statistics, reports, settings, and the assistant. Graphics layers
 * already on screen (placement, a press) are flattened into the same
 * recording and make the spike worse; the recording happens without them too.
 *
 * This settle stays on the direct draw path during the fling. Past the bound
 * it shifts the current canvas by at most [MaxVisual] and eases back. The
 * scroll container already clips that shift. No RenderNode is allocated when
 * the bound is reached, so the frame stays in the same budget as a scrolling
 * frame.
 *
 * `animateItem` appearance is not this hitch. Compose plays it only when the
 * item key was missing from the previous key index map, and [ledgerAppear]
 * refuses to replay after its first window.
 */
internal object SettleOverscroll {
    val MaxVisual = 12.dp

    /** Outward cushion. The return uses the rest of [LedgerMotion.Medium]. */
    const val GiveMillis = 100

    const val ReturnMillis = LedgerMotion.Medium - GiveMillis

    /** Leftover fling speed below this does not leave the bound. px/s. */
    const val MinFlingVelocity = 180f

    /** Speed that reaches [MaxVisual]. Faster flings stay at the cap. px/s. */
    const val KickReferenceVelocity = 4200f

    fun resisted(rawPx: Float, maxPx: Float): Float {
        if (rawPx == 0f || maxPx <= 0f) return 0f
        val sign = if (rawPx < 0f) -1f else 1f
        val t = abs(rawPx) / maxPx
        return sign * maxPx * (1f - 1f / (1f + t))
    }

    fun rawForVisual(visualPx: Float, maxPx: Float): Float {
        if (visualPx == 0f || maxPx <= 0f) return 0f
        val sign = if (visualPx < 0f) -1f else 1f
        val ratio = (abs(visualPx) / maxPx).coerceIn(0f, 0.98f)
        val t = 1f / (1f - ratio) - 1f
        return sign * t * maxPx
    }
}

@Immutable
internal class SettleOverscrollFactory(
    private val maxVisualPx: Float,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = SettleOverscrollEffect(maxVisualPx)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SettleOverscrollFactory) return false
        return maxVisualPx == other.maxVisualPx
    }

    override fun hashCode(): Int = maxVisualPx.hashCode()
}

@Composable
internal fun rememberSettleOverscrollFactory(): OverscrollFactory {
    val density = LocalDensity.current
    val maxVisualPx = with(density) { SettleOverscroll.MaxVisual.toPx() }
    return remember(maxVisualPx) { SettleOverscrollFactory(maxVisualPx) }
}

internal class SettleOverscrollEffect(
    private val maxVisualPx: Float,
    private val motionEnabled: () -> Boolean = { LedgerMotion.animationsEnabled() },
) : OverscrollEffect {
    private val drawNode = SettleDrawNode(this)
    private var releaseJob: Job? = null
    private var releaseGeneration = 0

    /** Finger pull before resistance, along the active axis. */
    private var pull = 0f
    private var verticalAxis = true

    var translation by mutableStateOf(Offset.Zero)
        private set

    override val isInProgress: Boolean
        get() = translation != Offset.Zero

    override val node: DelegatableNode
        get() = drawNode

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Fling frames (SideEffect) keep the direct draw. The cushion starts
        // from leftover velocity in applyToFling, after the list has stopped.
        if (!motionEnabled() || source != NestedScrollSource.UserInput || delta == Offset.Zero) {
            if (!motionEnabled() && translation != Offset.Zero) {
                pull = 0f
                moveTo(Offset.Zero)
            }
            return performScroll(delta)
        }
        takeOverFromRelease()
        val vertical = abs(delta.y) >= abs(delta.x)
        if (vertical != verticalAxis) {
            // Each scrollable feeds a single axis. A new axis drops the old cushion
            // instead of treating that pull as tension on the new axis.
            pull = 0f
            verticalAxis = vertical
            moveTo(Offset.Zero)
        }
        val main = if (vertical) delta.y else delta.x
        var remaining = main
        if (pull != 0f && remaining != 0f && signDiffers(pull, remaining)) {
            val next = pull + remaining
            if (abs(next) <= 0.5f || signDiffers(next, pull)) {
                remaining = if (signDiffers(next, pull) && abs(next) > 0.5f) next else 0f
                pull = 0f
            } else {
                pull = next
                remaining = 0f
            }
            setMain(vertical, SettleOverscroll.resisted(pull, maxVisualPx))
        }
        val afterRelax = if (vertical) Offset(delta.x, remaining) else Offset(remaining, delta.y)
        val consumedByScroll = performScroll(afterRelax)
        val scrollMain = if (vertical) consumedByScroll.y else consumedByScroll.x
        val leftover = remaining - scrollMain
        val absorbed = abs(leftover) > 0.5f
        if (absorbed) {
            pull += leftover
            setMain(vertical, SettleOverscroll.resisted(pull, maxVisualPx))
        }
        val relaxConsumed = main - remaining
        val pullConsumed = if (absorbed) leftover else 0f
        val mainConsumed = relaxConsumed + scrollMain + pullConsumed
        return if (vertical) {
            Offset(consumedByScroll.x, mainConsumed)
        } else {
            Offset(mainConsumed, consumedByScroll.y)
        }
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val hadTension = translation != Offset.Zero
        // Step the settle on the fling's frame clock. The draw node only owns
        // the job, so leaving the screen cancels it and the fling can return.
        val caller = coroutineContext
        if (!motionEnabled()) {
            pull = 0f
            moveTo(Offset.Zero)
        } else if (hadTension) {
            val vertical = verticalAxis
            val from = mainTranslation(vertical)
            launchRelease(caller) { generation ->
                animateMain(vertical, from, 0f, returnSpec(), generation)
            }
        }
        val consumed = performFling(velocity)
        if (!motionEnabled() || hadTension) return
        val leftover = velocity - consumed
        val vertical = abs(leftover.y) >= abs(leftover.x)
        val main = if (vertical) leftover.y else leftover.x
        if (abs(main) < SettleOverscroll.MinFlingVelocity) return
        val kick = (main / SettleOverscroll.KickReferenceVelocity * maxVisualPx)
            .coerceIn(-maxVisualPx, maxVisualPx)
        if (abs(kick) < 0.5f) return
        launchRelease(caller) { generation ->
            animateMain(vertical, 0f, kick, giveSpec(), generation)
            if (generation != releaseGeneration) return@launchRelease
            animateMain(vertical, kick, 0f, returnSpec(), generation)
        }
    }

    internal fun onNodeDetach() {
        releaseGeneration += 1
        releaseJob?.cancel()
        releaseJob = null
    }

    private fun takeOverFromRelease() {
        releaseGeneration += 1
        releaseJob?.cancel()
        releaseJob = null
        syncPullFromTranslation()
    }

    private fun syncPullFromTranslation() {
        pull = SettleOverscroll.rawForVisual(mainTranslation(verticalAxis), maxVisualPx)
    }

    private fun mainTranslation(vertical: Boolean): Float =
        if (vertical) translation.y else translation.x

    private fun setMain(vertical: Boolean, value: Float) {
        verticalAxis = vertical
        moveTo(if (vertical) Offset(0f, value) else Offset(value, 0f))
    }

    private fun moveTo(value: Offset) {
        translation = value
        if (drawNode.isAttached) drawNode.invalidateDraw()
    }

    private fun launchRelease(
        caller: CoroutineContext,
        block: suspend (generation: Int) -> Unit,
    ) {
        val generation = ++releaseGeneration
        releaseJob?.cancel()
        var context: CoroutineContext = EmptyCoroutineContext
        caller[MonotonicFrameClock]?.let { context += it }
        caller[ContinuationInterceptor]?.let { context += it }
        // Parent job stays the draw node's, so leaving the composition cancels
        // the settle. The caller's Job is intentionally not copied: applyToFling
        // returns as soon as the fling itself has stopped.
        releaseJob = drawNode.coroutineScope.launch(context) {
            block(generation)
            if (generation == releaseGeneration) {
                pull = 0f
                moveTo(Offset.Zero)
            }
        }
    }

    private suspend fun animateMain(
        vertical: Boolean,
        from: Float,
        to: Float,
        spec: AnimationSpec<Float>,
        generation: Int,
    ) {
        if (generation != releaseGeneration) return
        if (from == to) {
            setMain(vertical, to)
            return
        }
        animate(initialValue = from, targetValue = to, animationSpec = spec) { value, _ ->
            if (generation == releaseGeneration) setMain(vertical, value)
        }
    }

    private fun giveSpec(): FiniteAnimationSpec<Float> = LedgerMotion.spec(SettleOverscroll.GiveMillis)

    private fun returnSpec(): FiniteAnimationSpec<Float> = LedgerMotion.spec(SettleOverscroll.ReturnMillis)

    private fun signDiffers(a: Float, b: Float): Boolean =
        (a > 0f && b < 0f) || (a < 0f && b > 0f)
}

private class SettleDrawNode(
    private val effect: SettleOverscrollEffect,
) : Modifier.Node(), DrawModifierNode {
    override fun onDetach() {
        effect.onNodeDetach()
    }

    override fun ContentDrawScope.draw() {
        val offset = effect.translation
        if (offset == Offset.Zero) {
            drawContent()
        } else {
            translate(offset.x, offset.y) {
                this@draw.drawContent()
            }
        }
    }
}
