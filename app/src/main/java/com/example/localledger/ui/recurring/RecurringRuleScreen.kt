package com.example.localledger.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.example.localledger.domain.model.RecurrencePeriod
import com.example.localledger.domain.model.RecurringRule
import com.example.localledger.domain.model.ResumeChoice
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.AccountDropdownField
import com.example.localledger.ui.component.AmountField
import com.example.localledger.ui.component.CategoryIconGrid
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建周期规则")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorState(state.error ?: "读取周期规则失败", onRetry = viewModel::retry)
            }
            state.rules.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState("还没有周期规则。保存后会在打开应用或后台任务时自动入账。")
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "按日、周、月、年自动记一笔。同一规则的同一天只会生成一次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.rules, key = { it.id }) { rule ->
                    RuleRow(
                        modifier = Modifier.animateItem(),
                        rule = rule,
                        onEdit = { editing = rule },
                        onPause = { viewModel.pause(rule) },
                        onResume = { resumeTarget = rule },
                        onDelete = { deleteTarget = rule },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    resumeTarget?.let { rule ->
        ResumeRuleDialog(
            onBackfill = {
                resumeTarget = null
                viewModel.resume(rule, ResumeChoice.BACKFILL)
            },
            onSkip = {
                resumeTarget = null
                viewModel.resume(rule, ResumeChoice.SKIP_MISSED)
            },
            onDismiss = { resumeTarget = null },
        )
    }

    deleteTarget?.let { rule ->
        DeleteRuleDialog(
            onKeepTransactions = {
                deleteTarget = null
                viewModel.delete(rule, deleteGenerated = false)
            },
            onDeleteTransactions = {
                deleteTarget = null
                viewModel.delete(rule, deleteGenerated = true)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (creating || editing != null) {
        RuleDialog(
            state = state,
            existing = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { draft ->
                viewModel.save(draft)
                creating = false
                editing = null
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复这条周期规则？") },
        text = { Text("暂停期间错过的日期，可以现在补记，也可以跳到下一次，不再生成那些日期。") },
        confirmButton = {
            TextButton(onClick = onBackfill) { Text("补记错过的日期") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSkip) { Text("跳到下一次") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
internal fun DeleteRuleDialog(
    onKeepTransactions: () -> Unit,
    onDeleteTransactions: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除周期规则？") },
        text = { Text("规则删除后不再自动记账。已经生成的流水可以保留，也可以一并删除。") },
        confirmButton = {
            TextButton(onClick = onKeepTransactions) { Text("只删除规则") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDeleteTransactions) { Text("同时删除已生成流水") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleDialog(
    state: RecurringUiState,
    existing: RecurringRule?,
    onDismiss: () -> Unit,
    onSave: (RecurringDraft) -> Unit,
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
    var start by remember { mutableStateOf(existing?.startDate ?: LocalDate.now()) }
    var end by remember { mutableStateOf(existing?.endDate) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val accountCurrency = state.accounts.firstOrNull { it.id == accountId }?.currencyCode ?: state.currencyCode
    val toCurrency = state.accounts.firstOrNull { it.id == toAccountId }?.currencyCode ?: accountCurrency
    val categories = if (type == TransactionType.INCOME) state.incomeCategories else state.expenseCategories

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (existing == null) "新建周期规则" else "编辑周期规则",
                style = MaterialTheme.typography.titleLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = type == item,
                        onClick = { type = item },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                    ) { Text(typeLabel(item)) }
                }
            }
            AmountField(
                value = amount,
                onValueChange = { amount = it },
                label = "金额",
                currencyCode = accountCurrency,
            )
            if (type == TransactionType.TRANSFER) {
                AmountField(
                    value = toAmount,
                    onValueChange = { toAmount = it },
                    label = "转入金额，可留空按汇率计算",
                    currencyCode = toCurrency,
                )
            }
            Text("周期", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RecurrencePeriod.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = period == item,
                        onClick = { period = item },
                        shape = SegmentedButtonDefaults.itemShape(index, RecurrencePeriod.entries.size),
                    ) { Text(periodLabel(item)) }
                }
            }
            AccountDropdownField(
                label = "账户",
                accounts = state.accounts,
                selectedId = accountId,
                onSelect = { accountId = it },
            )
            if (type == TransactionType.TRANSFER) {
                AccountDropdownField(
                    label = "转入账户",
                    accounts = state.accounts,
                    selectedId = toAccountId,
                    onSelect = { toAccountId = it },
                )
            } else {
                Text("分类", style = MaterialTheme.typography.titleSmall)
                CategoryIconGrid(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickingStart = true }) {
                    Text("开始 ${DateTimeFormatters.date(start)}")
                }
                TextButton(onClick = { pickingEnd = true }) {
                    Text(end?.let { "结束 ${DateTimeFormatters.date(it)}" } ?: "结束日期，可留空")
                }
                if (end != null) {
                    TextButton(onClick = { end = null }) { Text("清除") }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        if (end != null && end!!.isBefore(start)) return@Button
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
                                startDate = start,
                                endDate = end,
                            ),
                        )
                    },
                ) { Text("保存") }
            }
        }
        }
    }

    if (pickingStart) {
        RuleDatePicker(
            initial = start,
            onDismiss = { pickingStart = false },
            onConfirm = {
                start = it
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        RuleDatePicker(
            initial = end ?: start,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                end = it
                pickingEnd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = pickerState.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    } ?: initial
                    onConfirm(date)
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = pickerState)
    }
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
