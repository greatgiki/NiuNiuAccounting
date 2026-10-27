package com.example.localledger.ui.importing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.LedgerSnackbarHost
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.data.repository.PendingDraft
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftInboxScreen(
    navController: NavHostController,
    viewModel: DraftInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DraftInboxEffect.Message -> snackbar.showSnackbar(effect.text)
                is DraftInboxEffect.Dismissed -> {
                    val result = snackbar.showSnackbar(effect.text, actionLabel = "撤销")
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(effect.draftId)
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待确认") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { LedgerSnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.ready -> LoadingState()
            state.drafts.isEmpty() -> EmptyState("没有待确认的记账")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                items(state.drafts, key = { it.id }) { draft ->
                    DraftCard(draft, state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: PendingDraft,
    state: DraftInboxUiState,
    viewModel: DraftInboxViewModel,
) {
    val categories = draftCategories(draft, state)
    LedgerCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(draft.counterparty.ifBlank { draft.source.label() }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${draft.source.label()} · ${DateTimeFormatters.dateTime(draft.transactionAt, viewModel.zoneId)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (draft.needsAmount) {
                        "需补全金额"
                    } else {
                        (if (draft.type == TransactionType.INCOME) "+" else "-") + MoneyFormatter.format(draft.amountMinor)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (draft.needsAmount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (draft.needsAmount) {
                Text(
                    "金额没有识别到。可以打开原来的账单自动补上，也可以在这里填写或忽略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (draft.sourceKey.isNotBlank()) {
                    TextButton(onClick = { viewModel.openBill(draft) }) { Text("打开原账单") }
                }
                var amountText by remember(draft.id) { mutableStateOf("") }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("补全金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                TextButton(onClick = { viewModel.setAmount(draft.id, amountText) }) { Text("保存金额") }
            }
            if (draft.likelyDuplicate) {
                Text("可能与已有流水重复", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            if (draft.type == TransactionType.TRANSFER) {
                OptionLine(
                    label = "转出",
                    value = state.accounts.firstOrNull { it.id == draft.accountId }?.name ?: "未选择",
                    options = state.accounts.map { it.id to it.name },
                    onSelect = { viewModel.assignTransfer(draft.id, it, draft.toAccountId) },
                )
                OptionLine(
                    label = "转入",
                    value = state.accounts.firstOrNull { it.id == draft.toAccountId }?.name ?: "未选择",
                    options = state.accounts.filter { it.id != draft.accountId }.map { it.id to it.name },
                    onSelect = { viewModel.assignTransfer(draft.id, draft.accountId, it) },
                )
            } else {
                OptionLine(
                    label = "分类",
                    value = categories.firstOrNull { it.id == draft.categoryId }?.name ?: "未选择",
                    options = categories.map { it.id to it.name },
                    onSelect = { viewModel.assign(draft.id, it, draft.accountId) },
                )
                OptionLine(
                    label = "账户",
                    value = state.accounts.firstOrNull { it.id == draft.accountId }?.name ?: "未选择",
                    options = state.accounts.map { it.id to it.name },
                    onSelect = { viewModel.assign(draft.id, draft.categoryId, it) },
                )
            }
            Row {
                TextButton(onClick = { viewModel.confirm(draft) }) { Text("确认") }
                TextButton(onClick = { viewModel.dismiss(draft.id) }) { Text("忽略") }
            }
        }
    }
}

@Composable
fun PendingDraftBanner(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: PendingDraftBadgeViewModel = hiltViewModel(),
) {
    val badge by viewModel.badge.collectAsStateWithLifecycle()
    if (badge.count <= 0) return
    LedgerCard(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        onClick = { navController.navigate(Routes.DRAFTS) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("待确认 ${badge.count}", style = MaterialTheme.typography.titleMedium)
            Text(
                if (badge.needsAmount > 0) {
                    "有 ${badge.needsAmount} 笔需补全金额，确认后才计入流水"
                } else {
                    "确认后才计入流水"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
