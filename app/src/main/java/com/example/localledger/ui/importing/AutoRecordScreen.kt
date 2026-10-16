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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.core.storage.AppSettings
import com.example.localledger.notification.isIgnoringBatteryOptimizations
import com.example.localledger.notification.isNotificationAccessEnabled
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    appSession: AppSession,
    navController: NavHostController,
) {
    val session by appSession.state.collectAsStateWithLifecycle()
    val settings = session.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accessEnabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = isNotificationAccessEnabled(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { appSession.updateSettings(transform) }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitch(
                        title = "开启自动记账",
                        subtitle = "监听微信和支付宝的付款、收款通知，先放入待确认",
                        checked = settings?.autoRecordEnabled == true,
                        onChecked = { enabled ->
                            update { it.copy(autoRecordEnabled = enabled) }
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    Text(
                        if (accessEnabled) "通知使用权：已开启" else "通知使用权：未开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (accessEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    ) { Text("打开系统通知使用权") }
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
                        title = "自动确认",
                        subtitle = "分类和账户都能匹配时直接记账，不再进入待确认",
                        checked = settings?.autoConfirmDrafts == true,
                        enabled = settings?.autoRecordEnabled == true,
                        onChecked = { enabled -> update { it.copy(autoConfirmDrafts = enabled) } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("电池优化", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (batteryUnrestricted) {
                            "当前未受电池优化限制。"
                        } else {
                            "部分手机在清理后台或省电策略下会停止通知监听。如果自动记账没有反应，请在系统设置里将本应用设为不限制电池优化。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) { Text("打开电池优化设置") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
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
