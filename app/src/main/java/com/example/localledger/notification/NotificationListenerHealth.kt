package com.example.localledger.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object NotificationListenerHealth {
    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    fun markConnected() {
        connectedState.value = true
    }

    fun markDisconnected() {
        connectedState.value = false
    }
}

object NotificationRebind {
    private val lastAutomaticRebind = AtomicLong(0)

    fun ensure(context: Context, force: Boolean = false) {
        enableIfDisabled(context)
        if (!isNotificationAccessEnabled(context)) return
        if (!force && NotificationListenerHealth.connected.value) return
        if (!force && !allowAutomaticRebind()) return
        val component = ComponentName(context, PaymentNotificationListenerService::class.java)
        NotificationListenerService.requestRebind(component)
    }

    private fun allowAutomaticRebind(): Boolean {
        val now = SystemClock.elapsedRealtime()
        while (true) {
            val previous = lastAutomaticRebind.get()
            if (now - previous < 5_000L) return false
            if (lastAutomaticRebind.compareAndSet(previous, now)) return true
        }
    }

    private fun enableIfDisabled(context: Context) {
        val component = ComponentName(context, PaymentNotificationListenerService::class.java)
        val state = context.packageManager.getComponentEnabledSetting(component)
        val disabled = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        if (!disabled) return
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

data class OemGuide(
    val title: String,
    val steps: String,
)

fun oemGuide(manufacturer: String, brand: String): OemGuide? {
    val name = "${manufacturer.lowercase()} ${brand.lowercase()}"
    return when {
        name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> OemGuide(
            title = "小米 / 红米 / POCO（MIUI、HyperOS）",
            steps = "打开安全中心的自启动管理，允许牛牛记账自启动。再到省电策略里把本应用设为无限制，并锁定最近任务，避免清理后台后通知监听断开。",
        )
        name.contains("huawei") || name.contains("honor") -> OemGuide(
            title = "华为 / 荣耀（EMUI、HarmonyOS）",
            steps = "在应用启动管理里把牛牛记账改为手动管理，并打开允许自启动、允许关联启动、允许后台活动。电池优化里选择不允许系统管理。",
        )
        name.contains("oppo") || name.contains("realme") || name.contains("oneplus") -> OemGuide(
            title = "OPPO / realme / 一加（ColorOS）",
            steps = "在自启动管理里允许牛牛记账。电池设置里关闭休眠和后台冻结，并在最近任务里锁定应用。",
        )
        name.contains("vivo") || name.contains("iqoo") -> OemGuide(
            title = "vivo / iQOO",
            steps = "在后台高耗电或自启动管理里允许牛牛记账，并在电池里把后台耗电设为允许高耗电。",
        )
        else -> null
    }
}

fun Context.openOemAutostartSettings(): Boolean {
    val candidates = listOf(
        intent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        intent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        intent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        intent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        intent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        intent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        intent("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        intent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        intent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        intent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
    )
    if (tryStart(candidates)) return true
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
    }
    return tryStart(listOf(details))
}

fun Context.openBatterySettings() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:$packageName".toUri()
    }
    if (tryStart(listOf(direct))) return
    tryStart(
        listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
    )
}

private fun intent(packageName: String, className: String): Intent {
    return Intent().setComponent(ComponentName(packageName, className))
}

private fun Context.tryStart(intents: List<Intent>): Boolean {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) {
            continue
        }
    }
    return false
}

fun canPostNotifications(context: Context): Boolean {
    val enabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (!enabled) return false
    if (Build.VERSION.SDK_INT < 33) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
