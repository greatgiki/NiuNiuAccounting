package com.example.localledger.capture

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var shown: View? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(content: View): Boolean {
        if (!canDraw()) return false
        hide()
        val width = context.resources.displayMetrics.widthPixels - dp(32)
        val params = WindowManager.LayoutParams(
            width.coerceAtLeast(dp(280)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(24) + navigationBar()
        }
        content.alpha = 0f
        content.translationY = dp(28).toFloat()
        return try {
            windowManager.addView(content, params)
            shown = content
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
            true
        } catch (error: Exception) {
            Log.w(TAG, "悬浮窗没有加上：${error.javaClass.simpleName}")
            shown = null
            false
        }
    }

    fun setTyping(enabled: Boolean) {
        val view = shown ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val base = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        params.flags = if (enabled) base else base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
            if (enabled) {
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // The sheet is hosted by an activity, which can take focus on its own.
        }
    }

    fun hide() {
        val view = shown ?: return
        shown = null
        view.animate().cancel()
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // The window was already gone.
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigationBar(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "NiuNiuCapture"
    }
}
