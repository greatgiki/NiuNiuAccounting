package com.example.localledger.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.localledger.domain.capture.PaymentPackages
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {
    @Inject lateinit var coordinator: CaptureCoordinator

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        serviceInfo = info.apply {
            packageNames = PaymentPackages.ALL
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 300
        }
        coordinator.onConnected(this)
    }

    override fun onDestroy() {
        pending?.let { main.removeCallbacks(it) }
        coordinator.onDisconnected()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::coordinator.isInitialized || event == null) return
        val flags = coordinator.flags()
        if (!flags.master) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName !in PaymentPackages.ALL || !flags.allows(packageName)) return
        val className = event.className?.toString().orEmpty()
        if (className.contains("password", ignoreCase = true) || className.contains("Pwd")) return
        // One read, 450ms after the page stops changing, so animations are not walked.
        pending?.let { main.removeCallbacks(it) }
        val task = Runnable {
            if (!coordinator.flags().master) return@Runnable
            val node = coordinator.snapshotOf(this, packageName) ?: return@Runnable
            coordinator.onSnapshot(packageName, node)
        }
        pending = task
        main.postDelayed(task, 450)
    }

    override fun onInterrupt() = Unit
}
