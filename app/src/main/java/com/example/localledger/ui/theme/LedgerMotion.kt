package com.example.localledger.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Short, eased motion. Interaction feedback stays inside 200–300ms. */
object LedgerMotion {
    const val Short = 200
    const val Medium = 280

    fun <T> short(): FiniteAnimationSpec<T> = tween(durationMillis = Short, easing = FastOutSlowInEasing)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(durationMillis = Medium, easing = FastOutSlowInEasing)
}
