package com.example.localledger.ui.platform

import android.view.View
import android.view.ViewGroup
import android.view.Window

/**
 * Compose 1.8's AndroidComposeView never sets [View.setOverScrollMode]. The
 * platform default is [View.OVER_SCROLL_IF_CONTENT_SCROLLS]. HyperOS can then
 * stretch the decor view when a fling or pull passes the edge, and that moves
 * the whole window — top bars and cards included — even after Compose
 * overscroll is turned off.
 *
 * [View.OVER_SCROLL_NEVER] is the opt-out those builds honor. Apply it to the
 * decor view and every descendant, including the ComposeView added by
 * [androidx.activity.ComponentActivity.setContent].
 */
internal fun disableWindowOverscroll(window: Window) {
    val decor = window.decorView
    applyNeverOverscroll(decor)
    // AndroidComposeView is attached on the next traversal, after setContent.
    decor.post { applyNeverOverscroll(decor) }
}

internal fun applyNeverOverscroll(view: View) {
    view.overScrollMode = View.OVER_SCROLL_NEVER
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            applyNeverOverscroll(view.getChildAt(index))
        }
    }
}
