package com.example.localledger.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.example.localledger.ui.component.LedgerSnackbarHost
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
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType
import com.example.localledger.ui.AppSession
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.ui.common.CurrencyChips
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.AmountField
import com.example.localledger.ui.component.CollapsibleSectionHeader
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.SubmitGate

fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK_CARD -> "银行卡"
    AccountType.E_WALLET -> "电子钱包"
    AccountType.CREDIT_CARD -> "信用卡"
    AccountType.OTHER -> "其他"
}

fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Outlined.Payments
    AccountType.BANK_CARD -> Icons.Outlined.AccountBalance
    AccountType.E_WALLET -> Icons.Outlined.AccountBalanceWallet
    AccountType.CREDIT_CARD -> Icons.Outlined.CreditCard
    AccountType.OTHER -> Icons.Outlined.MoreHoriz
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManageScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: AccountManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by appSession.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editTarget by remember { mutableStateOf<Pair<Boolean, Account?>?>(null) }
    var archiveTarget by remember { mutableStateOf<Account?>(null) }
    var archivedExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveGate = remember { SubmitGate() }
    val archiveGate = remember { SubmitGate() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val active = state.balances.filter { !it.account.isArchived }
    val archived = state.balances.filter { it.account.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { LedgerSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                saveGate.reset()
                editTarget = Pair(true, null)
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建账户")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorState(state.error ?: "读取账户失败", onRetry = viewModel::retry)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                if (active.isEmpty()) {
                    item(key = "empty") { EmptyState("暂无账户，点击右下角新建") }
                }
                items(active, key = { it.account.id }, contentType = { "account" }) { balance ->
                    AccountRow(
                        modifier = Modifier.animateItem(),
                        name = balance.account.name,
                        type = balance.account.type,
                        balanceMinor = balance.balanceMinor,
                        currencyCode = balance.account.currencyCode,
                        onEdit = {
                            saveGate.reset()
                            editTarget = Pair(false, balance.account)
                        },
                        onArchive = { archiveTarget = balance.account },
                    )
                    HorizontalDivider()
                }
                if (archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        CollapsibleSectionHeader(
                            title = "已停用（${archived.size}）",
                            expanded = archivedExpanded,
                            onToggle = { archivedExpanded = !archivedExpanded },
                        )
                    }
                    if (archivedExpanded) {
                    items(archived, key = { "archived-${it.account.id}" }, contentType = { "archived-account" }) { balance ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    balance.account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    accountTypeLabel(balance.account.type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(
                                amountMinor = balance.balanceMinor,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                currencyCode = balance.account.currencyCode,
                            )
                        }
                        HorizontalDivider()
                    }
                    }
                }
            }
        }
    }

    editTarget?.let { (_, account) ->
        AccountEditDialog(
            account = account,
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onConfirm = { name, type, balanceText, sortText, currency ->
                val ledgerId = session.currentLedgerId ?: return@AccountEditDialog
                saveGate.launch(scope, {
                    viewModel.save(account, ledgerId, name, type, balanceText, sortText, currency)
                }) {
                    editTarget = null
                }
            },
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    editTarget = null
                }
            },
        )
    }

    archiveTarget?.let { account ->
        ConfirmDeleteDialog(
            title = "停用账户“${account.name}”？",
            message = "停用后不能再用于新流水，历史流水和余额仍会保留。",
            confirmLabel = "停用",
            submitting = archiveGate.submitting,
            errorMessage = archiveGate.error,
            onConfirm = {
                archiveGate.launch(scope, { viewModel.archive(account.id) }) {
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
private fun AccountRow(
    name: String,
    type: AccountType,
    balanceMinor: Long,
    currencyCode: String,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${accountTypeLabel(type)} · ${currencyLabel(currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(amountMinor = balanceMinor, currencyCode = currencyCode)
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("停用") },
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
        }
    }
}

@Composable
private fun AccountEditDialog(
    account: Account?,
    onConfirm: (name: String, type: AccountType, balanceText: String, sortText: String, currency: String) -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: AccountType.CASH) }
    var currency by remember { mutableStateOf(account?.currencyCode ?: "CNY") }
    var balanceText by remember {
        mutableStateOf(account?.let { MoneyFormatter.minorToInput(it.initialBalanceMinor, it.currencyCode) } ?: "0")
    }
    var sortText by remember { mutableStateOf(account?.sortOrder?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新建账户" else "编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AccountType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(accountTypeLabel(t)) },
                        )
                        Spacer(Modifier.padding(2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("币种", style = MaterialTheme.typography.bodySmall)
                CurrencyChips(selected = currency, onSelect = { currency = it })
                if (account != null) {
                    Text(
                        "已有流水的账户不能改币种。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AmountField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = "初始余额（可为负数）",
                    currencyCode = currency,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sortText,
                    onValueChange = { sortText = it.filter(Char::isDigit).take(5) },
                    label = { Text("排序（数字越小越靠前）") },
                    singleLine = true,
                    enabled = !submitting,
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
                onClick = { if (!submitting) onConfirm(name, type, balanceText, sortText, currency) },
                enabled = name.isNotBlank() && !submitting,
            ) { Text(if (submitting) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}

private fun currencyLabel(code: String): String =
    CurrencyCatalog.supported.firstOrNull { it.code == code }?.label ?: code
