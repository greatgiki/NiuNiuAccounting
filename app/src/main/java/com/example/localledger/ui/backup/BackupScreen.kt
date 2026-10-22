package com.example.localledger.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.localledger.data.backup.BackupDocument
import com.example.localledger.data.backup.BackupFailure
import com.example.localledger.data.backup.BackupSummary
import com.example.localledger.data.backup.BackupValidator
import com.example.localledger.data.backup.LedgerBackupRepository
import com.example.localledger.data.work.AutoBackupCoordinator
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.home.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@Immutable
data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val askRestorePassword: Boolean = false,
    val preview: BackupSummary? = null,
    val autoEnabled: Boolean = false,
    val folderLabel: String? = null,
    val keepCount: Int = 5,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: LedgerBackupRepository,
    private val coordinator: AutoBackupCoordinator,
    private val appSession: AppSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutableState.asStateFlow()
    private var pendingDocument: BackupDocument? = null
    private var pendingUri: Uri? = null

    init {
        viewModelScope.launch {
            appSession.state.collect { session ->
                val settings = session.settings ?: return@collect
                val label = withContext(Dispatchers.IO) {
                    coordinator.folderLabel(settings.autoBackupTreeUri)
                }
                mutableState.update {
                    it.copy(
                        autoEnabled = settings.autoBackupEnabled && !settings.autoBackupTreeUri.isNullOrBlank(),
                        folderLabel = label,
                        keepCount = settings.autoBackupKeepCount.coerceIn(1, 20),
                    )
                }
            }
        }
    }

    fun exportTo(uri: Uri, password: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null, isError = false) }
            val result = runCatching { backupRepository.writeTo(uri, password.ifBlank { null }) }
            publish(result, "备份已保存到所选位置")
        }
    }

    fun inspect(uri: Uri, password: String?) {
        pendingUri = uri
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null, isError = false, preview = null) }
            val result = runCatching { backupRepository.readFrom(uri, password?.ifBlank { null }) }
            result.fold(
                onSuccess = { document ->
                    val summary = runCatching { BackupValidator.validate(document) }
                    summary.fold(
                        onSuccess = { value ->
                            pendingDocument = document
                            mutableState.update {
                                it.copy(busy = false, askRestorePassword = false, preview = value, message = null, isError = false)
                            }
                        },
                        onFailure = { error ->
                            pendingDocument = null
                            publish(Result.failure(error), "")
                        },
                    )
                },
                onFailure = { error ->
                    if (error is BackupFailure && error.kind == BackupFailure.Kind.NeedsPassword) {
                        mutableState.update { it.copy(busy = false, askRestorePassword = true, message = null, isError = false) }
                    } else {
                        publish(Result.failure(error), "")
                    }
                },
            )
        }
    }

    fun confirmRestore() {
        val document = pendingDocument ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, preview = null) }
            val result = runCatching {
                backupRepository.restore(document)
                val current = appSession.state.value.currentLedgerId
                val next = backupRepository.preferredLedgerId(document, current)
                if (next != null && next != current) appSession.switchLedger(next)
            }
            pendingDocument = null
            publish(result, "已用备份覆盖当前账本")
        }
    }

    fun dismissPreview() {
        pendingDocument = null
        mutableState.update { it.copy(preview = null) }
    }

    fun dismissPassword() {
        mutableState.update { it.copy(askRestorePassword = false) }
    }

    fun submitRestorePassword(password: String) {
        val uri = pendingUri ?: return
        inspect(uri, password)
    }

    fun setAutoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null, isError = false) }
            val result = runCatching { coordinator.setEnabled(enabled) }
            publish(result, if (enabled) "已开启自动备份" else "已关闭自动备份")
        }
    }

    fun useFolder(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null, isError = false) }
            val result = runCatching { coordinator.setFolder(uri) }
            publish(result, "已选择自动备份文件夹，稍后会写入一份备份")
        }
    }

    fun setKeepCount(count: Int) {
        viewModelScope.launch {
            runCatching { coordinator.setKeepCount(count) }
                .onFailure { error -> publish(Result.failure(error), "") }
        }
    }

    private fun publish(result: Result<Any?>, success: String) {
        if (result.exceptionOrNull() is CancellationException) return
        mutableState.update {
            it.copy(
                busy = false,
                askRestorePassword = false,
                message = result.fold(
                    onSuccess = { success },
                    onFailure = { error -> error.message?.takeIf { text -> text.isNotBlank() } ?: "操作失败，请重试" },
                ),
                isError = result.isFailure,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(navController: NavHostController, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var showExportPassword by remember { mutableStateOf(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) viewModel.exportTo(uri, password)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspect(uri, null)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.useFolder(uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        BackupRestoreContent(
            state = state,
            onExport = { showExportPassword = true },
            onRestore = { openBackup.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
            onToggleAuto = { enabled ->
                if (enabled && state.folderLabel == null) pickFolder.launch(null) else viewModel.setAutoEnabled(enabled)
            },
            onPickFolder = { pickFolder.launch(null) },
            onKeepCount = viewModel::setKeepCount,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        )
    }
    if (showExportPassword) {
        PasswordDialog(
            title = "导出备份",
            body = "可以设置密码。留空则不加密。密码只用于这一份文件，应用不会记住它；忘记后无法打开。",
            confirmLabel = "选择保存位置",
            requireMatch = true,
            allowEmpty = true,
            onDismiss = { showExportPassword = false },
            onConfirm = { password ->
                showExportPassword = false
                exportPassword = password
                val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
                createBackup.launch("牛牛记账备份-$stamp.nnbk")
            },
        )
    }
    if (state.askRestorePassword) {
        PasswordDialog(
            title = "输入备份密码",
            body = "这份备份已加密。",
            confirmLabel = "继续",
            requireMatch = false,
            allowEmpty = false,
            onDismiss = viewModel::dismissPassword,
            onConfirm = viewModel::submitRestorePassword,
        )
    }
    state.preview?.let { preview ->
        RestoreConfirmDialog(
            summary = preview,
            busy = state.busy,
            onConfirm = viewModel::confirmRestore,
            onDismiss = { if (!state.busy) viewModel.dismissPreview() },
        )
    }
}

@Composable
fun BackupRestoreContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onKeepCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("手动备份")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("把全部账本写成一个文件", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "文件里是各张表的 JSON，再打包压缩。可以选择密码，使用 AES-GCM 加密，密钥由 PBKDF2 从密码推导。文件由你自己保管，可以放到网盘或另一台设备。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onExport, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.busy) "正在处理…" else "导出备份")
                }
                TextButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("从文件恢复")
                }
            }
        }
        SectionTitle("自动备份")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("大约每天备份一次", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "备份到你选择的文件夹，只保留最近几份。自动备份不加密。系统会挑选合适的时间运行，不一定准时到点。需要密码时，请使用上面的「导出备份」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("开启自动备份", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.autoEnabled, onCheckedChange = onToggleAuto, enabled = !state.busy)
                }
                Text(
                    state.folderLabel ?: "尚未选择文件夹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPickFolder, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.folderLabel == null) "选择文件夹" else "更换文件夹")
                }
                Text("保留份数", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                val options = listOf(3, 5, 10)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = state.keepCount == count,
                            onClick = { onKeepCount(count) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            enabled = !state.busy,
                        ) { Text("$count 份") }
                    }
                }
            }
        }
        state.message?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun RestoreConfirmDialog(
    summary: BackupSummary,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val whenText = DateTimeFormatters.dateTime(summary.createdAtEpochMillis, ZoneId.systemDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用这份备份覆盖当前数据？") },
        text = {
            Column {
                Text("备份时间 $whenText")
                Text("数据库版本 v${summary.schemaVersion}")
                Text("账本 ${summary.ledgerCount} 个，账户 ${summary.accountCount} 个，流水 ${summary.transactionCount} 笔")
                Spacer(Modifier.height(8.dp))
                Text(
                    "恢复会用备份替换当前的账本、账户、流水、预算、周期规则、汇率缓存和待确认草稿。此操作不能在应用内撤销。写入前会先在本机留一份安全副本；如果写入失败，当前数据保持原样。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "正在恢复…" else "覆盖并恢复") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmLabel: String,
    requireMatch: Boolean,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text(if (allowEmpty) "密码（可选）" else "密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireMatch) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.take(64) },
                        label = { Text("再输入一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    password.isEmpty() && !allowEmpty -> "请输入密码"
                    password.isNotEmpty() && password.length < 4 -> "密码至少 4 位"
                    requireMatch && password != confirm -> "两次输入不一致"
                    else -> null
                }
                if (error == null) onConfirm(password)
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
