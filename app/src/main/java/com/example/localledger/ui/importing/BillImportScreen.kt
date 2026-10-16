package com.example.localledger.ui.importing

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillImportScreen(
    navController: NavHostController,
    viewModel: BillImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFile(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { snackbar.showSnackbar(it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入账单") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            LoadingState()
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "选择微信支付或支付宝导出的账单。微信邮件里的附件通常是带密码的压缩包，请先解压，再选择里面的 xlsx 或 csv。支付宝导出的 csv 多为 GBK，可以直接选择。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/zip",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                ) { Text(if (state.rows.isEmpty()) "选择账单文件" else "重新选择") }
            }
            if (state.error != null) {
                item { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }
            }
            if (state.resultMessage != null && state.rows.isEmpty()) {
                item { Text(state.resultMessage ?: "", style = MaterialTheme.typography.titleSmall) }
            }
            if (state.rows.isNotEmpty()) {
                item {
                    Text(
                        "${state.source?.label().orEmpty()} · ${state.fileName}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.skippedSummary.isNotBlank()) {
                        Text(state.skippedSummary, style = MaterialTheme.typography.bodySmall)
                    }
                    AccountChoice(
                        label = "本渠道默认账户",
                        selectedId = state.defaultAccountId,
                        accounts = state.accounts,
                        onSelect = viewModel::onDefaultAccount,
                    )
                    Row {
                        TextButton(onClick = viewModel::selectImportable) { Text("选择未重复") }
                        TextButton(onClick = viewModel::clearSelection) { Text("全不选") }
                    }
                }
                items(state.rows, key = { it.index }) { row ->
                    ImportRowCard(row, state, viewModel)
                }
                item {
                    val selected = state.rows.count { it.selected }
                    Button(
                        onClick = viewModel::importSelected,
                        enabled = !state.importing && selected > 0,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    ) { Text(if (state.importing) "正在导入" else "导入所选 $selected 笔") }
                }
            }
        }
    }
}

@Composable
private fun ImportRowCard(
    row: ImportRowState,
    state: BillImportUiState,
    viewModel: BillImportViewModel,
) {
    val categories = if (row.type == TransactionType.INCOME) state.incomeCategories else state.expenseCategories
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.selected,
                    onCheckedChange = { viewModel.toggle(row.index) },
                    enabled = row.flagLabel() != "已导入" && row.flagLabel() != "文件内重复",
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.counterparty.ifBlank { row.goods }.ifBlank { "未知对方" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        DateTimeFormatters.dateTime(row.transactionAt, viewModel.zoneId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    (if (row.type == TransactionType.INCOME) "+" else "-") + MoneyFormatter.format(row.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (row.type == TransactionType.INCOME) "收入" else "支出",
                style = MaterialTheme.typography.labelMedium,
            )
            row.flagLabel()?.let { label ->
                Text(label, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            if (row.paymentMethod.isNotBlank()) {
                Text("支付方式 ${row.paymentMethod}", style = MaterialTheme.typography.bodySmall)
            }
            OptionLine(
                label = "分类",
                value = categories.firstOrNull { it.id == row.categoryId }?.name ?: "未选择",
                options = categories.map { it.id to it.name },
                onSelect = { viewModel.onCategory(row.index, it) },
            )
            OptionLine(
                label = "账户",
                value = state.accounts.firstOrNull { it.id == row.accountId }?.name ?: "未选择",
                options = state.accounts.map { it.id to it.name },
                onSelect = { viewModel.onAccount(row.index, it) },
            )
        }
    }
}

@Composable
private fun AccountChoice(
    label: String,
    selectedId: String?,
    accounts: List<com.example.localledger.domain.model.Account>,
    onSelect: (String) -> Unit,
) {
    OptionLine(
        label = label,
        value = accounts.firstOrNull { it.id == selectedId }?.name ?: "按支付方式自动匹配",
        options = accounts.map { it.id to it.name },
        onSelect = onSelect,
    )
}

@Composable
internal fun OptionLine(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("$label：$value") }
    if (open) {
        OptionPickerDialog(
            title = label,
            options = options,
            onSelect = {
                open = false
                onSelect(it)
            },
            onDismiss = { open = false },
        )
    }
}
