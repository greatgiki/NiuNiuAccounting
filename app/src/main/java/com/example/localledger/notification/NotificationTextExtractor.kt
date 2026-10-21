package com.example.localledger.notification

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import com.example.localledger.domain.importing.NotificationText

object NotificationTextExtractor {
    fun extract(notification: Notification): String {
        val extras = notification.extras
        val parts = mutableListOf<CharSequence?>()
        parts += extras?.getCharSequence(Notification.EXTRA_TITLE)
        parts += extras?.getCharSequence(Notification.EXTRA_TEXT)
        parts += extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        parts += extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)
        parts += extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
        extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts += it }
        messageTexts(extras).forEach { parts += it }
        parts += notification.tickerText
        return NotificationText.join(parts)
    }

    private fun messageTexts(extras: Bundle?): List<CharSequence> {
        if (extras == null || !extras.containsKey(Notification.EXTRA_MESSAGES)) return emptyList()
        val raw = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        } ?: return emptyList()
        return raw.mapNotNull { item ->
            val bundle = item as? Bundle ?: return@mapNotNull null
            bundle.getCharSequence("text")
        }
    }
}
