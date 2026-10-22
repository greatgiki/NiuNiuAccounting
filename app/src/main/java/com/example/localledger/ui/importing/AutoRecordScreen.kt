package com.example.localledger.ui.importing

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.localledger.ui.component.LedgerCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.core.storage.AppSettings
import com.example.localledger.notification.NotificationDiagnostic
import com.example.localledger.notification.NotificationRebind
import com.example.localledger.notification.canPostNotifications
import com.example.localledger.notification.isIgnoringBatteryOptimizations
import com.example.localledger.notification.isNotificationAccessEnabled
import com.example.localledger.notification.oemGuide
import com.example.localledger.notification.openBatterySettings
import com.example.localledger.notification.openOemAutostartSettings
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: AutoRecordViewModel = hiltViewModel(),
) {
    val session by appSession.state.collectAsStateWithLifecycle()
    val settings = session.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val testMessage by viewModel.testMessage.collectAsStateWithLifecycle()
    var accessEnabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var notificationsAllowed by remember { mutableStateOf(canPostNotifications(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, connected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = isNotificationAccessEnabled(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                notificationsAllowed = canPostNotifications(context)
                if (accessEnabled && !connected) NotificationRebind.ensure(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = canPostNotifications(context)
    }
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { appSession.updateSettings(transform) }
    }
    val guide = remember { oemGuide(Build.MANUFACTURER, Build.BRAND) }
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动记账") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitch(
                        title = "开启自动记账",
                        subtitle = "监听微信和支付宝的付款、收款通知，先放入待确认",
                        checked = settings?.autoRecordEnabled == true,
                        onChecked = { enabled ->
                            update { it.copy(autoRecordEnabled = enabled) }
                            if (enabled) {
                                NotificationRebind.ensure(context, force = true)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                    )
                    StatusLine(
                        text = if (accessEnabled) "通知使用权：已开启" else "通知使用权：未开启",
                        ok = accessEnabled,
                    )
                    StatusLine(
                        text = when {
                            !accessEnabled -> "监听服务：未连接"
                            connected -> "监听服务：已连接"
                            else -> "监听服务：未连接。系统已授权，但服务没有绑上，常见于清理后台之后"
                        },
                        ok = accessEnabled && connected,
                    )
                    StatusLine(
                        text = if (notificationsAllowed) "本机通知权限：已开启" else "本机通知权限：未开启，确认提醒发不出来，草稿仍会出现在首页",
                        ok = notificationsAllowed,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    ) { Text("打开系统通知使用权") }
                    if (accessEnabled && !connected) {
                        TextButton(onClick = { NotificationRebind.ensure(context, force = true) }) {
                            Text("重新绑定监听")
                        }
                        Text(
                            "若仍显示未连接，请到通知使用权里先关掉牛牛记账，再重新打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("授予通知权限")
                        }
                    }
                    SettingSwitch(
                        title = "微信",
                        subtitle = "解析微信支付的付款和收款通知",
                        checked = settings?.wechatNotificationEnabled != false,
                        enabled = settings?.autoRecordEnabled == true,
                        onChecked = { enabled -> update { it.copy(wechatNotificationEnabled = enabled) } },
                    )
                    SettingSwitch(
                        title = "支付宝",
                        subtitle = "解析支付宝的付款和收款通知",
                        checked = settings?.alipayNotificationEnabled != false,
                        enabled = settings?.autoRecordEnabled == true,
                        onChecked = { enabled -> update { it.copy(alipayNotificationEnabled = enabled) } },
                    )
                    SettingSwitch(
                        title = "没有金额时也生成待确认",
                        subtitle = "通知里只有「微信支付凭证」这类文字时，先记一条需补全金额的草稿",
                        checked = settings?.keepAmountlessDrafts != false,
                        enabled = settings?.autoRecordEnabled == true,
                        onChecked = { enabled -> update { it.copy(keepAmountlessDrafts = enabled) } },
                    )
                    SettingSwitch(
                        title = "自动确认",
                        subtitle = "分类和账户都能匹配、并且金额齐全时直接记账",
                        checked = settings?.autoConfirmDrafts == true,
                        enabled = settings?.autoRecordEnabled == true,
                        onChecked = { enabled -> update { it.copy(autoConfirmDrafts = enabled) } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("电池优化", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (batteryUnrestricted) {
                            "当前未受电池优化限制。"
                        } else {
                            "部分手机在清理后台或省电策略下会停止通知监听。如果自动记账没有反应，请允许忽略电池优化，并把本应用加入自启动。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { context.openBatterySettings() }) { Text("请求忽略电池优化") }
                }
            }
            if (guide != null) {
                Spacer(Modifier.height(12.dp))
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(guide.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            guide.steps,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { context.openOemAutostartSettings() }) { Text("打开自启动或应用详情") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("最近收到的通知", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "只保存在本机，最多 20 条微信或支付宝通知，用来看有没有收到、以及为什么没记上。金额和商户名会打码后再保存。测试按钮会走真实开关和解析；能生成草稿但真机付款没反应时，多半是监听没连上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::simulate) { Text("测试") }
                    TextButton(onClick = viewModel::clearDiagnostics, enabled = recent.isNotEmpty()) { Text("清除记录") }
                    testMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (recent.isEmpty()) {
                        Text(
                            "还没有收到微信或支付宝通知。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        recent.forEach { item ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DiagnosticRow(item, zone)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("隐私", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "通知内容只在本机解析并生成待确认记账，不会上传。不使用无障碍服务。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { navController.navigate(Routes.DRAFTS) }) { Text("查看待确认") }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(item: NotificationDiagnostic, zone: ZoneId) {
    Text(
        "${DateTimeFormatters.dateTime(item.receivedAt, zone)} · ${item.outcome}",
        style = MaterialTheme.typography.labelMedium,
    )
    if (item.patternId != null) {
        Text(item.patternId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
    Text(
        item.excerpt.ifBlank { "（没有读到文字）" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
