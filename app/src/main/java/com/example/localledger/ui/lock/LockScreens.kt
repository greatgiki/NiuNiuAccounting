package com.example.localledger.ui.lock

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.localledger.security.AppLockController
import com.example.localledger.security.AppLockState
import com.example.localledger.security.BiometricUnlockActivity
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.rememberLedgerHaptic
import com.example.localledger.ui.home.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val controller: AppLockController,
) : ViewModel() {
    val lockState: StateFlow<AppLockState> = controller.state
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    fun setup(pin: String, confirm: String) = runAction { controller.setup(pin, confirm) ?: "已开启应用锁" }

    fun change(oldPin: String, newPin: String, confirm: String) =
        runAction { controller.change(oldPin, newPin, confirm) ?: "密码已更新" }

    fun disable(pin: String) = runAction { controller.disable(pin) ?: "已关闭应用锁" }

    fun setBiometric(enabled: Boolean) = runAction { controller.setBiometric(enabled) ?: if (enabled) "已开启生物识别" else "已关闭生物识别" }

    fun setTimeout(minutes: Int) {
        viewModelScope.launch { controller.setTimeout(minutes) }
    }

    fun setMaskWidgetAmounts(enabled: Boolean) {
        viewModelScope.launch { controller.setMaskWidgetAmounts(enabled) }
    }

    private fun runAction(block: suspend () -> String) {
        viewModelScope.launch {
            val text = runCatching { block() }.getOrElse { it.message ?: "操作失败，请重试" }
            mutableMessage.value = text
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(navController: NavHostController, viewModel: AppLockViewModel = hiltViewModel()) {
    val state by viewModel.lockState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    var flow by remember { mutableStateOf<PinFlow>(PinFlow.None) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (flow == PinFlow.None) "应用锁" else "输入密码") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (flow == PinFlow.None) navController.popBackStack() else flow = PinFlow.None
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (flow == PinFlow.None) {
            AppLockSettingsContent(
                hasPin = state.hasPin,
                biometricEnabled = state.biometricEnabled,
                biometricAvailable = biometricAvailable,
                timeoutMinutes = state.timeoutMinutes,
                message = message,
                onCreate = { flow = PinFlow.CreateFirst },
                onChange = { flow = PinFlow.ChangeOld },
                onDisable = { flow = PinFlow.Disable },
                onBiometric = viewModel::setBiometric,
                onTimeout = viewModel::setTimeout,
                maskWidgetAmounts = state.maskWidgetAmounts,
                onMaskWidgetAmounts = viewModel::setMaskWidgetAmounts,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            )
        } else {
            PinFlowContent(
                flow = flow,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                onAdvance = { entered ->
                    when (val current = flow) {
                        PinFlow.CreateFirst -> flow = PinFlow.CreateConfirm(entered)
                        is PinFlow.CreateConfirm -> {
                            viewModel.setup(current.pin, entered)
                            flow = PinFlow.None
                        }
                        PinFlow.ChangeOld -> flow = PinFlow.ChangeNew(entered)
                        is PinFlow.ChangeNew -> flow = PinFlow.ChangeConfirm(current.old, entered)
                        is PinFlow.ChangeConfirm -> {
                            viewModel.change(current.old, current.pin, entered)
                            flow = PinFlow.None
                        }
                        PinFlow.Disable -> {
                            viewModel.disable(entered)
                            flow = PinFlow.None
                        }
                        PinFlow.None -> Unit
                    }
                },
            )
        }
    }
}

private sealed interface PinFlow {
    data object None : PinFlow
    data object CreateFirst : PinFlow
    data class CreateConfirm(val pin: String) : PinFlow
    data object ChangeOld : PinFlow
    data class ChangeNew(val old: String) : PinFlow
    data class ChangeConfirm(val old: String, val pin: String) : PinFlow
    data object Disable : PinFlow
}

@Composable
fun AppLockSettingsContent(
    hasPin: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    timeoutMinutes: Int,
    message: String?,
    onCreate: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onTimeout: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maskWidgetAmounts: Boolean = true,
    onMaskWidgetAmounts: (Boolean) -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("数字密码")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (hasPin) "已开启。冷启动以及离开应用超过设定时间后，需要密码才能进入。"
                    else "设置 4 到 6 位数字密码。密码只以盐和摘要保存在本机，应用无法还原原文。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasPin) {
                    TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) { Text("设置密码") }
                } else {
                    TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) { Text("修改密码") }
                    TextButton(onClick = onDisable, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭应用锁") }
                }
            }
        }
        if (hasPin) {
            SectionTitle("离开多久后锁定")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    listOf(0 to "回到后台立即锁定", 1 to "1 分钟", 5 to "5 分钟", 15 to "15 分钟").forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onTimeout(minutes) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = timeoutMinutes == minutes, onClick = { onTimeout(minutes) })
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            SectionTitle("桌面小组件")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("锁屏时隐藏金额", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启应用锁后，桌面小组件用圆点代替收入、支出和结余。关闭后仍显示金额。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = maskWidgetAmounts, onCheckedChange = onMaskWidgetAmounts)
                }
            }
            SectionTitle("生物识别")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("指纹或生物识别", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (biometricAvailable) "可在密码之外使用本机已录入的生物识别解锁。"
                            else "这台设备没有可用的指纹或生物识别。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = biometricEnabled && biometricAvailable,
                        onCheckedChange = onBiometric,
                        enabled = biometricAvailable,
                    )
                }
            }
            SectionTitle("忘记密码")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "忘记密码后无法从应用内找回。摘要不能反推出原密码。要去掉应用锁，只能清除本应用的数据，账本会一起删除。请先在「备份与恢复」导出备份，再去系统设置里清除牛牛记账的数据。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PinFlowContent(flow: PinFlow, onAdvance: (String) -> Unit, modifier: Modifier = Modifier) {
    val title = when (flow) {
        PinFlow.CreateFirst -> "设置 4 到 6 位数字密码"
        is PinFlow.CreateConfirm -> "再输入一次"
        PinFlow.ChangeOld, PinFlow.Disable -> "输入当前密码"
        is PinFlow.ChangeNew -> "输入新密码"
        is PinFlow.ChangeConfirm -> "再输入一次新密码"
        PinFlow.None -> ""
    }
    var pin by remember(flow) { mutableStateOf("") }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        PinDots(pin.length)
        Spacer(Modifier.height(24.dp))
        PinKeypad(
            actionLabel = "下一步",
            actionEnabled = pin.length in 4..6,
            onDigit = { if (pin.length < 6) pin += it },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            onAction = { onAdvance(pin) },
        )
    }
}

@Composable
fun LockScreen(
    biometricAvailable: Boolean,
    error: String?,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    initialPin: String = "",
) {
    var pin by remember { mutableStateOf(initialPin) }
    var forgot by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = "应用已锁定" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("牛牛记账", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("已锁定", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "输入 4 到 6 位数字密码",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PinDots(pin.length)
        Spacer(Modifier.height(8.dp))
        Text(
            error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.heightIn(min = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        PinKeypad(
            actionLabel = if (busy) "…" else "解锁",
            actionEnabled = !busy && pin.length in 4..6,
            onDigit = { if (!busy && pin.length < 6) pin += it },
            onBackspace = { if (!busy && pin.isNotEmpty()) pin = pin.dropLast(1) },
            onAction = { onUnlock(pin) },
        )
        if (biometricAvailable) {
            TextButton(onClick = onBiometric, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Text("使用指纹或生物识别", modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = { forgot = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("忘记密码") }
    }
    if (forgot) ForgotPinDialog { forgot = false }
}

@Composable
fun ForgotPinDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无法找回密码") },
        text = {
            Text(
                "应用锁密码只以不可逆的摘要保存在本机，忘记后不能从应用里恢复，也不能用指纹改掉它。\n\n" +
                    "要继续使用，只能清除牛牛记账的数据。清除后账本、账户和流水都会删除。\n\n" +
                    "如果还进得去设置，请先导出备份。如果已经锁在外面，只能在能想起密码时进来备份，或接受清除数据后的结果。\n\n" +
                    "清除路径：系统设置 → 应用 → 牛牛记账 → 存储 → 清除数据。",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("我知道了") } },
    )
}

@Composable
private fun PinDots(length: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "已输入 $length 位" },
    ) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < length) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun PinKeypad(
    actionLabel: String,
    actionEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onAction: () -> Unit,
) {
    val haptic = rememberLedgerHaptic()
    val gap = 8.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    PinKey(key, Modifier.weight(1f).height(52.dp)) {
                        haptic()
                        onDigit(key)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            PinKey("", Modifier.weight(1f).height(52.dp), icon = {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "退格")
            }) {
                haptic()
                onBackspace()
            }
            PinKey("0", Modifier.weight(1f).height(52.dp)) {
                haptic()
                onDigit("0")
            }
            PinKey(
                actionLabel,
                Modifier.weight(1f).height(52.dp),
                enabled = actionEnabled,
                container = if (actionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                content = if (actionEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                haptic()
                onAction()
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) container else container.copy(alpha = 0.45f),
        contentColor = content,
        modifier = modifier.graphicsLayer {},
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) icon() else Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LockGate(controller: AppLockController, content: @Composable () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val biometricAvailable = state.biometricEnabled && remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val launchBiometric = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            controller.unlockWithBiometric()
            error = null
        } else {
            val message = result.data?.getStringExtra(BiometricUnlockActivity.EXTRA_MESSAGE)
            if (!message.isNullOrBlank()) error = message
        }
    }
    when {
        state.phase == AppLockState.Phase.Unknown -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        )
        state.locked -> LockScreen(
            biometricAvailable = biometricAvailable,
            error = if (state.unreadable) "无法读取应用锁。请先备份能打开的账本，再清除应用数据。" else error,
            busy = busy,
            onUnlock = { pin ->
                if (busy) return@LockScreen
                busy = true
                scope.launch {
                    val ok = controller.unlock(pin)
                    busy = false
                    error = if (ok) null else "密码不正确"
                }
            },
            onBiometric = {
                launchBiometric.launch(Intent(context, BiometricUnlockActivity::class.java))
            },
        )
        else -> content()
    }
}
