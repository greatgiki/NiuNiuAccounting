package com.example.localledger.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * While a nested horizontal control (swipe-to-delete, a sideways scroller) is
 * touched, the top-level pager stops accepting the same gesture.
 * Claims are counted so overlapping controls do not re-enable the pager early.
 */
@Stable
class PagerGestureGate {
    private var claims by mutableIntStateOf(0)
    var userScrollEnabled by mutableStateOf(true)
        private set

    val isClaimed: Boolean get() = claims > 0

    fun claim() {
        claims += 1
        userScrollEnabled = false
    }

    fun release() {
        claims = (claims - 1).coerceAtLeast(0)
        userScrollEnabled = claims == 0
    }
}

val LocalPagerGestureGate = staticCompositionLocalOf<PagerGestureGate?> { null }

/**
 * Observes pointer downs without consuming them, so the inner control still
 * receives the gesture while the pager is told to stand down.
 */
fun Modifier.claimPagerHorizontalGesture(): Modifier = composed {
    val gate = LocalPagerGestureGate.current ?: return@composed this
    pointerInput(gate) {
        var held = false
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.any { it.pressed }
                    if (down && !held) {
                        gate.claim()
                        held = true
                    } else if (!down && held) {
                        gate.release()
                        held = false
                    }
                }
            }
        } finally {
            if (held) gate.release()
        }
    }
}
