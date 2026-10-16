package com.example.localledger.ui.budget

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.SubmitGate
import com.example.localledger.ui.home.BudgetProgressRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetManageScreen(
    navController: NavHostController,
    viewModel: BudgetManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<BudgetEditor?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val saveGate = remember { SubmitGate() }
    val deleteGate = remember { SubmitGate() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BudgetEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算") },
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
                editor = BudgetEditor(null, null)
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建预算")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error ?: "读取预算失败", onRetry = viewModel::retry)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                item(key = "month") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previousMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一月")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(DateTimeFormatters.yearMonth(state.yearMonth), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = viewModel::nextMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一月")
                        }
                    }
                    Text(
                        "金额使用账本本位币 ${state.currencyCode}，支出进度来自当月流水。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.lines.isEmpty()) {
                    item(key = "empty") { EmptyState("这个月还没有预算") }
                }
                items(state.lines, key = { it.id }, contentType = { "budget" }) { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            BudgetProgressRow(line, state.currencyCode)
                        }
                        IconButton(onClick = {
                            saveGate.reset()
                            val budget = state.budgets.first { it.id == line.id }
                            editor = BudgetEditor(budget, budget.categoryId)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑${line.label}")
                        }
                        IconButton(onClick = { deleteTarget = line.id to line.label }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除${line.label}")
                        }
                    }
                    HorizontalDivider()
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editor?.let { target ->
        BudgetEditDialog(
            target = target,
            categories = state.categories,
            currencyCode = state.currencyCode,
            submitting = saveGate.submitting,
            errorMessage = saveGate.error,
            onDismiss = {
                if (!saveGate.submitting) {
                    saveGate.reset()
                    editor = null
                }
            },
            onConfirm = { categoryId, amountText ->
                val digits = CurrencyCatalog.of(state.currencyCode).minorDigits
                val amount = try {
                    MoneyParser.toMinor(amountText, digits)
                } catch (error: LedgerException) {
                    null
                }
                if (amount == null) {
                    saveGate.launch(scope, { UiOutcome.Failure("金额格式不正确") }) {}
                    return@BudgetEditDialog
                }
                saveGate.launch(scope, { viewModel.save(target.existing, categoryId, amount) }) {
                    editor = null
                }
            },
        )
    }

    deleteTarget?.let { (budgetId, label) ->
        ConfirmDeleteDialog(
            title = "删除「$label」预算？",
            message = "删除后无法恢复。",
            confirmLabel = "删除",
            submitting = deleteGate.submitting,
            errorMessage = deleteGate.error,
            onConfirm = {
                deleteGate.launch(scope, { viewModel.delete(budgetId) }) {
                    deleteTarget = null
                }
            },
            onDismiss = {
                deleteGate.reset()
                deleteTarget = null
            },
        )
    }
}

internal data class BudgetEditor(val existing: Budget?, val categoryId: String?)

@Composable
internal fun BudgetEditDialog(
    target: BudgetEditor,
    categories: List<com.example.localledger.domain.model.Category>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (String?, String) -> Unit,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var total by remember { mutableStateOf(target.categoryId == null) }
    var categoryId by remember { mutableStateOf(target.categoryId ?: categories.firstOrNull()?.id) }
    var amount by remember {
        mutableStateOf(target.existing?.let { MoneyFormatter.minorToInput(it.amountMinor, currencyCode) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.existing == null) "新建预算" else "编辑预算") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { total = true }) { Text(if (total) "总预算 ✓" else "总预算") }
                    TextButton(onClick = { total = false }) { Text(if (!total) "分类预算 ✓" else "分类预算") }
                }
                if (!total) {
                    categories.forEach { category ->
                        TextButton(onClick = { categoryId = category.id }) {
                            Text(if (categoryId == category.id) "● ${category.name}" else category.name)
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("预算金额") },
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
                onClick = {
                    if (!submitting) onConfirm(if (total) null else categoryId, amount)
                },
                enabled = !submitting && amount.isNotBlank() && (total || categoryId != null),
            ) { Text(if (submitting) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
    )
}
