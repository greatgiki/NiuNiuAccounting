package com.example.localledger.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Short, eased motion. Interaction feedback stays inside 140–280ms.
 * Specs collapse to a snap when the system animator scale is 0
 * ("remove animations").
 */
object LedgerMotion {
    const val Press = 140
    const val Short = 200
    const val Settle = 240
    const val Medium = 280

    /** Decelerates into rest. Used for navigation and entrances. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val Standard: Easing = FastOutSlowInEasing

    fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    fun <T> spec(durationMillis: Int, easing: Easing = Emphasized): FiniteAnimationSpec<T> {
        if (!animationsEnabled()) return snap()
        return tween(durationMillis = durationMillis, easing = easing)
    }

    fun <T> press(): FiniteAnimationSpec<T> = spec(Press)

    fun <T> short(): FiniteAnimationSpec<T> = spec(Short)

    fun <T> settle(): FiniteAnimationSpec<T> = spec(Settle)

    fun <T> medium(): FiniteAnimationSpec<T> = spec(Medium, Standard)
}
