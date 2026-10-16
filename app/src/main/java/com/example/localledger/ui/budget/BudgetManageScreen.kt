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
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
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
            FloatingActionButton(onClick = { editor = BudgetEditor(null, null) }) {
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
                item {
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
                    item { EmptyState("这个月还没有预算") }
                }
                items(state.lines, key = { it.id }) { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            BudgetProgressRow(line, state.currencyCode)
                        }
                        IconButton(onClick = {
                            val budget = state.budgets.first { it.id == line.id }
                            editor = BudgetEditor(budget, budget.categoryId)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "编辑${line.label}")
                        }
                        IconButton(onClick = { viewModel.delete(line.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除${line.label}")
                        }
                    }
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    editor?.let { target ->
        BudgetEditDialog(
            target = target,
            categories = state.categories,
            currencyCode = state.currencyCode,
            onDismiss = { editor = null },
            onConfirm = { categoryId, amountText ->
                val digits = CurrencyCatalog.of(state.currencyCode).minorDigits
                val amount = try {
                    MoneyParser.toMinor(amountText, digits)
                } catch (error: LedgerException) {
                    null
                }
                if (amount == null) return@BudgetEditDialog
                viewModel.save(target.existing, categoryId, amount)
                editor = null
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
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (total) null else categoryId, amount) },
                enabled = amount.isNotBlank() && (total || categoryId != null),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
