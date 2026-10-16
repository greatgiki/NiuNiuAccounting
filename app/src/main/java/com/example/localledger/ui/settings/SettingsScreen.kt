package com.example.localledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.component.SubmitGate
import com.example.localledger.ui.home.SectionTitle
import com.example.localledger.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appSession: AppSession,
    navController: NavHostController,
) {
    val session by appSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle("账本与分类")
        Card(modifier = Modifier.fillMaxWidth()) {
            SettingsEntry("账本管理") { navController.navigate(Routes.LEDGERS) }
            HorizontalDivider()
            SettingsEntry("账户管理") { navController.navigate(Routes.ACCOUNTS) }
            HorizontalDivider()
            SettingsEntry("支出分类") {
                navController.navigate(Routes.categories(CategoryType.EXPENSE))
            }
            HorizontalDivider()
            SettingsEntry("收入分类") {
                navController.navigate(Routes.categories(CategoryType.INCOME))
            }
            HorizontalDivider()
            SettingsEntry("预算") { navController.navigate(Routes.BUDGETS) }
            HorizontalDivider()
            SettingsEntry("周期记账") { navController.navigate(Routes.RECURRING) }
        }

        ExchangeRateSection()

        SectionTitle("外观")
        Card(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { scope.launch { appSession.setTheme(mode) } }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = session.themeMode == mode,
                        onClick = { scope.launch { appSession.setTheme(mode) } },
                    )
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (index < ThemeMode.entries.size - 1) HorizontalDivider()
            }
        }

        SectionTitle("数据与隐私")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "数据仅保存在本机",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "账本、账户和流水只保存在本机，不会上传。\n" +
                        "刷新汇率时优先读取中国外汇交易中心公布的人民币汇率中间价，只获取公开汇率，不发送账本内容。\n" +
                        "中间价未公布的币种再向其他公开接口补齐。没有网络时继续使用上次缓存的汇率。\n" +
                        "卸载应用、清除应用数据或恢复出厂设置后，账本数据将被删除且无法恢复。\n" +
                        "当前版本不支持备份与恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ExchangeRateSection(viewModel: ExchangeRateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRevalue by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val revalueGate = remember { SubmitGate() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { message = it }
    }
    SectionTitle("汇率")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本位币 ${state.currencyCode}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                state.updatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.source.isNotBlank()) {
                Text(
                    "来源 ${state.source}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "保存时写入当时的本位币金额。刷新汇率不会自动改历史。需要时可以按当前缓存重算，账户原币金额和手工填写的转入金额保持不变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (message != null) {
                Text(message ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                Text(if (state.refreshing) "刷新中…" else "刷新汇率")
            }
            TextButton(onClick = {
                revalueGate.reset()
                confirmRevalue = true
            }) { Text("按当前汇率重算历史") }
        }
    }
    if (confirmRevalue) {
        RevalueConfirmDialog(
            submitting = revalueGate.submitting,
            errorMessage = revalueGate.error,
            onConfirm = {
                revalueGate.launch(scope, { viewModel.revalueWithCurrentRates() }) {
                    confirmRevalue = false
                }
            },
            onDismiss = {
                if (!revalueGate.submitting) {
                    revalueGate.reset()
                    confirmRevalue = false
                }
            },
        )
    }
}

@Composable
internal fun RevalueConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按当前汇率重算？") },
        text = {
            Column {
                Text("将用当前缓存汇率重算本账本全部流水的本位币金额，不按各笔流水的日期挑选历史汇率。此操作不可撤销。")
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!submitting) onConfirm() }, enabled = !submitting) {
                Text(if (submitting) "重算中…" else "重算")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}

@Composable
private fun SettingsEntry(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "进入$label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
