package com.example.localledger.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, PaymentAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

fun Context.openAccessibilitySettings(): Boolean {
    return tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun Context.openOverlaySettings(): Boolean {
    val direct = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:$packageName".toUri(),
    )
    if (tryStart(direct)) return true
    return tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
}

private fun Context.tryStart(intent: Intent): Boolean {
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
