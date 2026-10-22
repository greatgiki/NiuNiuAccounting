package com.example.localledger.domain.widget

object WidgetPrivacy {
    const val MASK = "••••"

    /** Amounts stay visible unless the app lock is on and the user left masking enabled. */
    fun maskAmounts(lockConfigured: Boolean, maskWhenLocked: Boolean): Boolean {
        return lockConfigured && maskWhenLocked
    }
}
