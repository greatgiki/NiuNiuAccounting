package com.example.localledger.notification

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

fun isNotificationAccessEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return flat.contains(context.packageName)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
