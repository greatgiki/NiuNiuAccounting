package com.example.localledger.ui.recurring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
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
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.ResumeChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.SubmitGate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleScreen(
    navController: NavHostController,
    viewModel: RecurringRuleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var resumeTarget by remember { mutableStateOf<RecurringRule?>(null) }
    var deleteTarget by remember { mutableStateOf<RecurringRule?>(null) }
    val scope = rememberCoroutineScope()
    val saveGate = remember { SubmitGate() }
    val resumeGate = remember { SubmitGate() }
    val deleteGate = remember { SubmitGate() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecurringEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期记账") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                saveGate.reset()
                creating = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建周期规则")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error ?: "读取周期规则失败", onRetry = viewModel::retry)
            state.rules.isEmpty() -> EmptyState("还没有周期规则。保存后会在打开应用或后台任务时自动入账。")
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item(key = "intro") {
                    Text(
                        "按日、周、月、年自动记一笔。同一规则的同一天只会生成一次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.rules, key = { it.id }, contentType = { "rule" }) { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = {
                            saveGate.reset()
                            editing = rule
                        },
                        onPause = { viewModel.pause(rule) },
                        onResume = { resumeTarget = rule },
                        onDelete = { deleteTarget = rule },
                    )
                    HorizontalDivider()
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    resumeTarget?.let { rule ->
        ResumeRuleDialog(
            submitting = resumeGate.submitting,
            errorMessage = resumeGate.error,
            onBackfill = {
                resumeGate.launch(scope, { viewModel.resume(rule, ResumeChoice.BACKFILL) }) {
                    resumeTarget = null
                }
            },
            onSkip = {
                resumeGate.launch(scope, { viewModel.resume(rule, ResumeChoice.SKIP_MISSED) }) {
                    resumeTarget = null
                }
            },
            onDismiss = {
                if (!resumeGate.submitting) {
                    resumeGate.reset()
                    resumeTarget = null
                }
            },
        )
    }

    deleteTarget?.let { rule ->
        DeleteRuleDialog(
            submitting = deleteGate.submitting,
            errorMessage = deleteGate.error,
            onKeepTransactions = {
                deleteGate.launch(scope, { viewModel.delete(rule, deleteGenerated = false) }) {
                    deleteTarget = null
                }
            },
            onDeleteTransactions = {
                deleteGate.launch(scope, { viewModel.delete(rule, deleteGenerated = true) }) {
                    deleteTarget = null
                }
            },
            onDismiss = {
                if (!deleteGate.submitting) {
                    deleteGate.reset()
                    deleteTarget = null
                }
            },
        )
    }

    if (creating || editing != null) {
        RuleDialog(
            state = state,
            existing = editing,
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    creating = false
                    editing = null
                }
            },
            onSave = { draft ->
                saveGate.launch(scope, { viewModel.save(draft) }) {
                    creating = false
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: RecurringRule,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${periodLabel(rule.period)} · ${typeLabel(rule.type)} · ${rule.categoryName ?: rule.accountName}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "${MoneyFormatter.format(rule.amountMinor, rule.accountCurrencyCode)} · ${DateTimeFormatters.date(rule.startDate)} 起" +
                        (rule.endDate?.let { "，至 ${DateTimeFormatters.date(it)}" } ?: "") +
                        " · 下次 ${DateTimeFormatters.date(rule.nextRunDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = !rule.paused,
                onCheckedChange = { active -> if (active) onResume() else onPause() },
            )
        }
        Row {
            TextButton(onClick = onEdit) { Text("编辑") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除规则")
            }
            if (rule.paused) {
                Text("已暂停", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
internal fun ResumeRuleDialog(
    onBackfill: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复这条周期规则？") },
        text = {
            Column {
                Text("暂停期间错过的日期，可以现在补记，也可以跳到下一次，不再生成那些日期。")
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
            TextButton(onClick = onBackfill, enabled = !submitting) { Text("补记错过的日期") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSkip, enabled = !submitting) { Text("跳到下一次") }
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
            }
        },
    )
}

@Composable
internal fun DeleteRuleDialog(
    onKeepTransactions: () -> Unit,
    onDeleteTransactions: () -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除周期规则？") },
        text = {
            Column {
                Text("规则删除后不再自动记账。已经生成的流水可以保留，也可以一并删除。")
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
            TextButton(onClick = onKeepTransactions, enabled = !submitting) { Text("只删除规则") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDeleteTransactions, enabled = !submitting) { Text("同时删除已生成流水") }
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
            }
        },
    )
}

@Composable
internal fun RuleDialog(
    state: RecurringUiState,
    existing: RecurringRule?,
    onDismiss: () -> Unit,
    onSave: (RecurringDraft) -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.let { MoneyFormatter.minorToInput(it.amountMinor) } ?: "") }
    var toAmount by remember { mutableStateOf(existing?.toAmountMinor?.let { MoneyFormatter.minorToInput(it) } ?: "") }
    var accountId by remember { mutableStateOf(existing?.accountId ?: state.accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId ?: state.accounts.getOrNull(1)?.id) }
    var categoryId by remember {
        mutableStateOf(
            existing?.categoryId ?: state.expenseCategories.firstOrNull()?.id,
        )
    }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var period by remember { mutableStateOf(existing?.period ?: RecurrencePeriod.MONTHLY) }
    var start by remember { mutableStateOf(existing?.startDate?.toString() ?: LocalDate.now().toString()) }
    var end by remember { mutableStateOf(existing?.endDate?.toString().orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建周期规则" else "编辑周期规则") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    TransactionType.entries.forEach { item ->
                        TextButton(onClick = { type = item }) {
                            Text(if (type == item) "● ${typeLabel(item)}" else typeLabel(item))
                        }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, singleLine = true)
                if (type == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = toAmount,
                        onValueChange = { toAmount = it },
                        label = { Text("转入金额，可留空按汇率计算") },
                        singleLine = true,
                    )
                }
                Text("周期", modifier = Modifier.padding(top = 8.dp))
                Row {
                    RecurrencePeriod.entries.forEach { item ->
                        TextButton(onClick = { period = item }) {
                            Text(if (period == item) "● ${periodLabel(item)}" else periodLabel(item))
                        }
                    }
                }
                Text("账户", modifier = Modifier.padding(top = 8.dp))
                state.accounts.forEach { account ->
                    TextButton(onClick = { accountId = account.id }) {
                        Text(if (accountId == account.id) "● ${account.name}" else account.name)
                    }
                }
                if (type == TransactionType.TRANSFER) {
                    Text("转入账户")
                    state.accounts.forEach { account ->
                        TextButton(onClick = { toAccountId = account.id }) {
                            Text(if (toAccountId == account.id) "● ${account.name}" else account.name)
                        }
                    }
                } else {
                    Text("分类")
                    val categories = if (type == TransactionType.INCOME) state.incomeCategories else state.expenseCategories
                    categories.forEach { category ->
                        TextButton(onClick = { categoryId = category.id }) {
                            Text(if (categoryId == category.id) "● ${category.name}" else category.name)
                        }
                    }
                }
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("开始日期 yyyy-MM-dd") }, singleLine = true)
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("结束日期，可留空") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("备注") }, singleLine = true, enabled = !submitting)
                val shownError = localError ?: errorMessage
                if (!shownError.isNullOrBlank()) {
                    Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (submitting) return@TextButton
                    val startDate = runCatching { LocalDate.parse(start) }.getOrNull()
                    if (startDate == null) {
                        localError = "开始日期格式应为 yyyy-MM-dd"
                        return@TextButton
                    }
                    val endDate = end.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (end.isNotBlank() && endDate == null) {
                        localError = "结束日期格式应为 yyyy-MM-dd"
                        return@TextButton
                    }
                    localError = null
                    onSave(
                        RecurringDraft(
                            existing = existing,
                            type = type,
                            amountText = amount,
                            toAmountText = toAmount,
                            accountId = accountId,
                            toAccountId = toAccountId,
                            categoryId = categoryId,
                            note = note,
                            period = period,
                            startDate = startDate,
                            endDate = endDate,
                        ),
                    )
                },
            ) { Text(if (submitting) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
    )
}

private fun typeLabel(type: TransactionType) = when (type) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

private fun periodLabel(period: RecurrencePeriod) = when (period) {
    RecurrencePeriod.DAILY -> "每天"
    RecurrencePeriod.WEEKLY -> "每周"
    RecurrencePeriod.MONTHLY -> "每月"
    RecurrencePeriod.YEARLY -> "每年"
}
