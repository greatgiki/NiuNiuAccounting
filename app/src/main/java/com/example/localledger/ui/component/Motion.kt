package com.example.localledger.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.localledger.ui.theme.LedgerMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Items composed after this window (scrolling) appear in place, without a second entrance. */
private const val APPEAR_WINDOW_MILLIS = 420L

private const val APPEAR_LIMIT = 8

private const val APPEAR_STAGGER_MILLIS = 24

@Stable
class LedgerEntranceClock(private val startedAtNanos: Long = System.nanoTime()) {
    fun elapsedMillis(): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L
}

private val NoEntrance = LedgerEntranceClock(startedAtNanos = 0L)

val LocalLedgerEntrance = staticCompositionLocalOf { NoEntrance }

@Composable
fun rememberLedgerEntrance(): LedgerEntranceClock = remember { LedgerEntranceClock() }

/**
 * Fade and an 8dp rise for the first screenful. The layer is removed once the
 * animation ends so scrolling does not keep extra graphics layers.
 */
@Composable
fun Modifier.ledgerAppear(index: Int): Modifier {
    if (index !in 0 until APPEAR_LIMIT || !LedgerMotion.animationsEnabled()) return this
    val clock = LocalLedgerEntrance.current
    val play = remember(clock, index) { clock.elapsedMillis() < APPEAR_WINDOW_MILLIS }
    if (!play) return this
    val progress = remember(clock, index) { Animatable(0f) }
    var running by remember(clock, index) { mutableStateOf(true) }
    LaunchedEffect(clock, index) {
        val delayMs = index * APPEAR_STAGGER_MILLIS
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, LedgerMotion.settle())
        running = false
    }
    if (!running) return this
    return graphicsLayer {
        val fraction = progress.value
        alpha = fraction
        translationY = (1f - fraction) * 8.dp.toPx()
    }
}

/**
 * Press scale driven by the same [MutableInteractionSource] as the clickable.
 * The graphics layer exists only while the press animation is running.
 */
@Composable
fun Modifier.ledgerPress(interactionSource: MutableInteractionSource): Modifier {
    val scale = remember { Animatable(1f) }
    var active by remember { mutableStateOf(false) }
    val motion = LedgerMotion.animationsEnabled()
    LaunchedEffect(interactionSource, motion) {
        if (!motion) {
            scale.snapTo(1f)
            active = false
            return@LaunchedEffect
        }
        var job: Job? = null
        interactionSource.interactions.collect { event ->
            val pressing = when (event) {
                is PressInteraction.Press -> true
                is PressInteraction.Release, is PressInteraction.Cancel -> false
                else -> return@collect
            }
            job?.cancel()
            job = launch {
                if (pressing) active = true
                scale.animateTo(if (pressing) 0.985f else 1f, LedgerMotion.press())
                if (!pressing) active = false
            }
        }
    }
    if (!active) return this
    return graphicsLayer {
        val fraction = scale.value
        scaleX = fraction
        scaleY = fraction
    }
}

@Composable
fun Modifier.ledgerClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .ledgerPress(interaction)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Soft scale-in for a dialog card. The dialog window itself closes when back commits. */
@Composable
fun Modifier.ledgerSurfaceEnter(): Modifier {
    if (!LedgerMotion.animationsEnabled()) return this
    val progress = remember { Animatable(0.96f) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, LedgerMotion.short())
        running = false
    }
    if (!running) return this
    return graphicsLayer {
        val fraction = progress.value.coerceIn(0.96f, 1f)
        val t = (fraction - 0.96f) / 0.04f
        alpha = 0.86f + 0.14f * t
        scaleX = fraction
        scaleY = fraction
    }
}

@Composable
fun LedgerAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.ledgerSurfaceEnter(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = Modifier.ledgerSurfaceEnter(),
        content = content,
    )
}

@Composable
fun LedgerCrossfadeText(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            fadeIn(LedgerMotion.short()) togetherWith fadeOut(LedgerMotion.short())
        },
        label = "amount",
    ) { value ->
        content(value)
    }
}
