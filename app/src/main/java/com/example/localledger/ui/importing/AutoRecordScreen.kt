package com.example.localledger.ui.importing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.capture.isAccessibilityServiceEnabled
import com.example.localledger.capture.openAccessibilitySettings
import com.example.localledger.capture.openOverlaySettings
import com.example.localledger.core.storage.AppSettings
import com.example.localledger.domain.capture.NamedChoice
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
import com.example.localledger.ui.component.LedgerCard
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
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val testMessage by viewModel.testMessage.collectAsStateWithLifecycle()
    var accessibilityOn by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var overlayOn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var notificationsAllowed by remember { mutableStateOf(canPostNotifications(context)) }
    var listenerOn by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    var smsOn by remember { mutableStateOf(hasSmsPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, connected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = isAccessibilityServiceEnabled(context)
                overlayOn = Settings.canDrawOverlays(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                notificationsAllowed = canPostNotifications(context)
                listenerOn = isNotificationAccessEnabled(context)
                smsOn = hasSmsPermission(context)
                if (listenerOn && !connected && settings?.notificationFallbackEnabled != false) {
                    NotificationRebind.ensure(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = canPostNotifications(context)
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsOn = granted
    }
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { appSession.updateSettings(transform) }
    }
    val guide = remember { oemGuide(Build.MANUFACTURER, Build.BRAND) }
    val zone = remember { ZoneId.systemDefault() }
    var picking by remember { mutableStateOf<ChoicePicker?>(null) }
    val enabled = settings?.autoRecordEnabled == true

    picking?.let { picker ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(picker.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    picker.options.forEach { option ->
                        TextButton(
                            onClick = {
                                picker.onPick(option.id)
                                picking = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("关闭") } },
        )
    }

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
                    Text("隐私", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "自动记账默认关闭。打开后，只在本机阅读微信、支付宝和云闪付的支付成功页、账单详情，以及微信里打开的微信支付服务通知。银行短信和银行 App 通知要单独允许。通知里如果是「消费**元」，会先留下待确认，点按后打开原来的账单再补上金额。点「记下」才写入账本。不上传屏幕和短信，不读取支付密码。可以随时关掉。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitch(
                        title = "开启自动记账",
                        subtitle = "识别支付页面后弹出确认。关闭时不读取任何页面。",
                        checked = enabled,
                        onChecked = { turnedOn ->
                            update { it.copy(autoRecordEnabled = turnedOn) }
                            if (turnedOn && Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    SettingSwitch(
                        title = "微信",
                        subtitle = "支付成功页和账单详情",
                        checked = settings?.wechatNotificationEnabled != false,
                        enabled = enabled,
                        onChecked = { value -> update { it.copy(wechatNotificationEnabled = value) } },
                    )
                    SettingSwitch(
                        title = "支付宝",
                        subtitle = "支付成功页和账单详情",
                        checked = settings?.alipayNotificationEnabled != false,
                        enabled = enabled,
                        onChecked = { value -> update { it.copy(alipayNotificationEnabled = value) } },
                    )
                    SettingSwitch(
                        title = "云闪付",
                        subtitle = "支付成功页和交易详情",
                        checked = settings?.unionPayEnabled != false,
                        enabled = enabled,
                        onChecked = { value -> update { it.copy(unionPayEnabled = value) } },
                    )
                    SettingSwitch(
                        title = "银行短信",
                        subtitle = "新短信和银行 App 通知。解析金额、卡尾号和收支方向。不读短信信箱。",
                        checked = settings?.bankSmsEnabled == true,
                        enabled = enabled,
                        onChecked = { value ->
                            if (!value) {
                                update { it.copy(bankSmsEnabled = false) }
                            } else {
                                if (!smsOn) smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                update { it.copy(bankSmsEnabled = true) }
                            }
                        },
                    )
                    if (enabled && settings?.bankSmsEnabled == true && !smsOn) {
                        StatusLine("短信权限未授予，银行短信暂不读取。银行 App 通知仍可用。", false)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("在 HyperOS 上保持可用", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "小米的支付保护会挡住支付页面上的悬浮窗。识别到之后先记着，离开微信、支付宝或云闪付再弹出确认。也可以点状态栏里的那条通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusLine(if (accessibilityOn) "无障碍：已开启" else "无障碍：未开启", accessibilityOn)
                    StatusLine(if (overlayOn) "悬浮窗：已允许" else "悬浮窗：未允许", overlayOn)
                    StatusLine(
                        if (notificationsAllowed) "通知：已允许，用于常驻状态和离开页面后的确认" else "通知：未允许，确认提醒发不出来",
                        notificationsAllowed,
                    )
                    StatusLine(
                        if (batteryUnrestricted) "电池优化：已忽略" else "电池优化：仍可能在后台清掉自动记账",
                        batteryUnrestricted,
                    )
                    TextButton(onClick = { context.openAccessibilitySettings() }) { Text("打开无障碍设置") }
                    TextButton(onClick = { context.openOverlaySettings() }) { Text("允许悬浮窗") }
                    if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("授予通知权限")
                        }
                    }
                    TextButton(onClick = { context.openBatterySettings() }) { Text("忽略电池优化") }
                    TextButton(onClick = { context.openOemAutostartSettings() }) { Text("打开自启动或应用详情") }
                    Text(
                        "自启动：在安全中心允许牛牛记账自启动。最近任务：按住本应用的卡片，点锁定，避免被清理后悬浮窗不再出现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (guide != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(guide.title, style = MaterialTheme.typography.labelLarge)
                        Text(guide.steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("默认账本", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "没认出商家时用这里的分类和账户。认出过的商家会记住你上次确认的分类和账户。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceRow("账本", nameOf(catalog.ledgers, settings?.captureLedgerId, "当前账本")) {
                        picking = ChoicePicker("默认账本", listOf(NamedChoice("", "当前账本")) + catalog.ledgers) { id ->
                            update { it.copy(captureLedgerId = id) }
                        }
                    }
                    ChoiceRow("账户", nameOf(catalog.accounts, settings?.captureAccountId, "按页面猜测")) {
                        picking = ChoicePicker("默认账户", listOf(NamedChoice("", "按页面猜测")) + catalog.accounts) { id ->
                            update { it.copy(captureAccountId = id) }
                        }
                    }
                    ChoiceRow("支出分类", nameOf(catalog.expenseCategories, settings?.captureExpenseCategoryId, "按商家猜测")) {
                        picking = ChoicePicker("默认支出分类", listOf(NamedChoice("", "按商家猜测")) + catalog.expenseCategories) { id ->
                            update { it.copy(captureExpenseCategoryId = id) }
                        }
                    }
                    ChoiceRow("收入分类", nameOf(catalog.incomeCategories, settings?.captureIncomeCategoryId, "按商家猜测")) {
                        picking = ChoicePicker("默认收入分类", listOf(NamedChoice("", "按商家猜测")) + catalog.incomeCategories) { id ->
                            update { it.copy(captureIncomeCategoryId = id) }
                        }
                    }
                    ChoiceRow("转入账户", nameOf(catalog.accounts, settings?.captureTransferAccountId, "另一个账户")) {
                        picking = ChoicePicker("默认转入账户", listOf(NamedChoice("", "另一个账户")) + catalog.accounts) { id ->
                            update { it.copy(captureTransferAccountId = id) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("通知补充", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "金额被写成「消费**元」时，先记一条待确认。点这条提醒会打开原来的账单，页面识别到金额后再确认。约三分钟内，金额和方向相同的页面、通知、服务通知和银行短信只记一笔。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitch(
                        title = "通知补充",
                        subtitle = "沿用已有的付款通知解析",
                        checked = settings?.notificationFallbackEnabled != false,
                        enabled = enabled,
                        onChecked = { value ->
                            update { it.copy(notificationFallbackEnabled = value) }
                            if (value) NotificationRebind.ensure(context, force = true)
                        },
                    )
                    StatusLine(if (listenerOn) "通知使用权：已开启" else "通知使用权：未开启", listenerOn)
                    StatusLine(
                        if (listenerOn && connected) "监听服务：已连接" else "监听服务：未连接",
                        listenerOn && connected,
                    )
                    SettingSwitch(
                        title = "没有金额时也生成待确认",
                        subtitle = "「消费**元」或只有「微信支付凭证」时，先记一条待补金额的草稿",
                        checked = settings?.keepAmountlessDrafts != false,
                        enabled = enabled && settings?.notificationFallbackEnabled != false,
                        onChecked = { value -> update { it.copy(keepAmountlessDrafts = value) } },
                    )
                    SettingSwitch(
                        title = "通知可以直接记账",
                        subtitle = "分类和账户都能匹配、金额齐全、且不是重复金额时直接记账",
                        checked = settings?.autoConfirmDrafts == true,
                        enabled = enabled && settings?.notificationFallbackEnabled != false,
                        onChecked = { value -> update { it.copy(autoConfirmDrafts = value) } },
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("打开系统通知使用权") }
                    TextButton(onClick = viewModel::simulate) { Text("测试通知解析") }
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
                    Text("做不到的地方", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "只适配简体中文界面。微信、支付宝改版后，成功页或账单详情的用词变了就会暂时认不出。服务通知要打开才能读到明文金额。聊天、密码页和未完成的转账不会记。银行短信和银行 App 通知只认常见大陆银行的入账和支出，验证码会丢掉。已配置的 AI 助手只在商家对不上分类时用来猜分类，不发送页面内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { navController.navigate(Routes.DRAFTS) }) { Text("查看待确认") }
                }
            }
        }
    }
}

private data class ChoicePicker(
    val title: String,
    val options: List<NamedChoice>,
    val onPick: (String) -> Unit,
)

private fun nameOf(options: List<NamedChoice>, id: String?, fallback: String): String {
    if (id.isNullOrBlank()) return fallback
    return options.firstOrNull { it.id == id }?.name ?: fallback
}

private fun hasSmsPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title)
            Text(value, color = MaterialTheme.colorScheme.primary)
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
