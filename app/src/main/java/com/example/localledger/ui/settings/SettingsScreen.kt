package com.example.localledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.localledger.BuildConfig
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.home.SectionTitle
import com.example.localledger.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            SettingsEntry("账本管理", Icons.AutoMirrored.Filled.MenuBook) { navController.navigate(Routes.LEDGERS) }
            HorizontalDivider()
            SettingsEntry("账户管理", Icons.Default.AccountBalanceWallet) { navController.navigate(Routes.ACCOUNTS) }
            HorizontalDivider()
            SettingsEntry("支出分类", Icons.AutoMirrored.Filled.TrendingDown) {
                navController.navigate(Routes.categories(CategoryType.EXPENSE))
            }
            HorizontalDivider()
            SettingsEntry("收入分类", Icons.AutoMirrored.Filled.TrendingUp) {
                navController.navigate(Routes.categories(CategoryType.INCOME))
            }
            HorizontalDivider()
            SettingsEntry("预算", Icons.Default.Savings) { navController.navigate(Routes.BUDGETS) }
            HorizontalDivider()
            SettingsEntry("周期记账", Icons.Default.EventRepeat) { navController.navigate(Routes.RECURRING) }
        }

        ExchangeRateSection()

        SectionTitle("外观")
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = session.themeMode == mode,
                        onClick = { scope.launch { appSession.setTheme(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            },
                        )
                    }
                }
            }
        }

        SectionTitle("数据与隐私")
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
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

        SectionTitle("关于")
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Text("关于", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}",
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
            TextButton(onClick = viewModel::refresh) { Text("刷新汇率") }
            TextButton(onClick = { confirmRevalue = true }) { Text("按当前汇率重算历史") }
        }
    }
    if (confirmRevalue) {
        RevalueConfirmDialog(
            onConfirm = {
                confirmRevalue = false
                viewModel.revalueWithCurrentRates()
            },
            onDismiss = { confirmRevalue = false },
        )
    }
}

@Composable
internal fun RevalueConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按当前汇率重算？") },
        text = {
            Text("将用当前缓存汇率重算本账本全部流水的本位币金额，不按各笔流水的日期挑选历史汇率。此操作不可撤销。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("重算") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SettingsEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
