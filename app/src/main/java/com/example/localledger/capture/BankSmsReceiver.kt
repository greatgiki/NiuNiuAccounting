package com.example.localledger.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.localledger.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: CaptureCoordinator

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!::coordinator.isInitialized || !::appScope.isInitialized) return
        val pending = goAsync()
        val receivedAt = System.currentTimeMillis()
        appScope.launch {
            try {
                if (!coordinator.bankSmsActive()) return@launch
                val body = smsBody(intent)
                if (body.isNotEmpty()) coordinator.onBankMessage(body, receivedAt)
            } finally {
                pending.finish()
            }
        }
    }

    private fun smsBody(intent: Intent): String {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
                .mapNotNull { message -> message?.messageBody }
                .joinToString(separator = "")
                .trim()
        } catch (_: Exception) {
            ""
        }
    }
}
