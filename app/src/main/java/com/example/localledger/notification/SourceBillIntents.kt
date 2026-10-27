package com.example.localledger.notification

import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import com.example.localledger.domain.capture.CaptureDedup

/**
 * Holds the payment notification's [contentIntent] so a reminder can open that bill.
 * The intent stays on device. Nothing is uploaded.
 */
object SourceBillIntents {
    private const val LIMIT = 32
    private val intents = LinkedHashMap<String, PendingIntent>()

    @Volatile
    private var service: PaymentNotificationListenerService? = null

    @Volatile
    private var focusedKey: String? = null

    @Volatile
    private var focusedAt: Long = 0L

    fun attach(listener: PaymentNotificationListenerService) {
        service = listener
    }

    fun detach(listener: PaymentNotificationListenerService) {
        if (service === listener) service = null
    }

    fun remember(key: String, intent: PendingIntent) {
        if (key.isBlank()) return
        synchronized(intents) {
            intents.remove(key)
            intents[key] = intent
            while (intents.size > LIMIT) {
                val eldest = intents.keys.firstOrNull() ?: break
                intents.remove(eldest)
            }
        }
    }

    fun rememberActive(notifications: Array<StatusBarNotification>?) {
        notifications.orEmpty().forEach { notification ->
            val key = notification.key ?: return@forEach
            val intent = notification.notification?.contentIntent ?: return@forEach
            remember(key, intent)
        }
    }

    fun send(key: String): Boolean {
        if (key.isBlank()) return false
        focusedKey = key
        focusedAt = System.currentTimeMillis()
        val cached = synchronized(intents) { intents[key] }
        if (cached != null && fire(cached)) return true
        val fresh = service?.findContentIntent(key) ?: return false
        remember(key, fresh)
        return fire(fresh)
    }

    fun peekFocus(now: Long = System.currentTimeMillis()): String? {
        val key = focusedKey ?: return null
        if (now - focusedAt > CaptureDedup.WINDOW_MILLIS) {
            focusedKey = null
            return null
        }
        return key
    }

    fun clearFocus() {
        focusedKey = null
    }

    private fun fire(intent: PendingIntent): Boolean {
        return try {
            intent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
