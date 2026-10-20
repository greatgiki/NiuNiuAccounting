package com.example.localledger.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.Ledger
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.CurrencyChips
import com.example.localledger.ui.component.CollapsibleSectionHeader
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.SubmitGate
import com.example.localledger.ui.home.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerManageScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: LedgerManageViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Ledger?>(null) }
    var archiveTarget by remember { mutableStateOf<Ledger?>(null) }
    var archivedExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveGate = remember { SubmitGate() }
    val archiveGate = remember { SubmitGate() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LedgerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val active = session.ledgers.filter { !it.isArchived }
    val archived = session.ledgers.filter { it.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账本管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { com.example.localledger.ui.component.LedgerSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                saveGate.reset()
                showCreateDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建账本")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item(key = "active-title") { SectionTitle("使用中") }
            items(active, key = { it.id }, contentType = { "ledger" }) { ledger ->
                LedgerRow(
                    ledger = ledger,
                    isCurrent = ledger.id == session.currentLedgerId,
                    onSwitch = { viewModel.switchTo(ledger.id) },
                    onRename = {
                        saveGate.reset()
                        renameTarget = ledger
                    },
                    onArchive = { archiveTarget = ledger },
                )
                HorizontalDivider()
            }
            if (archived.isNotEmpty()) {
                item(key = "archived-header") {
                    CollapsibleSectionHeader(
                        title = "已归档（${archived.size}）",
                        expanded = archivedExpanded,
                        onToggle = { archivedExpanded = !archivedExpanded },
                    )
                }
                if (archivedExpanded) {
                    items(archived, key = { "archived-${it.id}" }, contentType = { "archived-ledger" }) { ledger ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                ledger.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameInputDialog(
            title = "新建账本",
            confirmLabel = "创建",
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onConfirm = { name, currency ->
                saveGate.launch(scope, { viewModel.create(name, currency) }) {
                    showCreateDialog = false
                }
            },
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    showCreateDialog = false
                }
            },
        )
    }

    renameTarget?.let { ledger ->
        NameInputDialog(
            title = "修改账本",
            initialValue = ledger.name,
            initialCurrency = ledger.currencyCode,
            confirmLabel = "保存",
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onConfirm = { name, currency ->
                saveGate.launch(scope, { viewModel.rename(ledger, name, currency) }) {
                    renameTarget = null
                }
            },
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    renameTarget = null
                }
            },
        )
    }

    archiveTarget?.let { ledger ->
        ConfirmDeleteDialog(
            title = "归档账本“${ledger.name}”？",
            message = "归档后不能再记录新流水，历史数据仍会保留。",
            confirmLabel = "归档",
            submitting = archiveGate.submitting,
            errorMessage = archiveGate.error,
            onConfirm = {
                archiveGate.launch(scope, { viewModel.archive(ledger) }) {
                    archiveTarget = null
                }
            },
            onDismiss = {
                archiveGate.reset()
                archiveTarget = null
            },
        )
    }
}

@Composable
private fun LedgerRow(
    ledger: Ledger,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSwitch)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ledger.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                buildString {
                    append(currencyLabel(ledger.currencyCode))
                    if (isCurrent) append(" · 当前账本")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (!isCurrent) {
                DropdownMenuItem(
                    text = { Text("切换到此账本") },
                    onClick = {
                        menuExpanded = false
                        onSwitch()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("修改名称") },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("归档") },
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
        }
    }
}

@Composable
fun NameInputDialog(
    title: String,
    initialValue: String = "",
    initialCurrency: String = "CNY",
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var value by remember { mutableStateOf(initialValue) }
    var currency by remember { mutableStateOf(initialCurrency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(30) },
                    singleLine = true,
                    label = { Text("名称") },
                )
                Text("本位币", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                CurrencyChips(selected = currency, onSelect = { currency = it })
                Text(
                    "汇总、统计和预算按本位币计算。更改本位币会重算历史流水的本位币金额：优先用不晚于该笔时间的汇率缓存，如果流水早于全部缓存，则用当前汇率。账户原币金额不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
            TextButton(
                onClick = { if (!submitting) onConfirm(value.trim(), currency) },
                enabled = value.isNotBlank() && !submitting,
            ) { Text(if (submitting) "保存中…" else confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}

private fun currencyLabel(code: String): String =
    CurrencyCatalog.supported.firstOrNull { it.code == code }?.let { "${it.label} ${it.code}" } ?: code
