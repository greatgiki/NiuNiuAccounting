package com.example.localledger.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.TransactionRowContent
import com.example.localledger.ui.home.transactionSubtitle
import com.example.localledger.ui.home.transactionTitle
import com.example.localledger.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionListEffect.ShowMessage -> message = effect.message
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = { Text("搜索备注") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "筛选")
            }
        }

        val filter = state.filter
        val hasFilter = !filter.isEmpty || state.keyword.isNotBlank()
        if (hasFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                filter.type?.let {
                    FilterChipLabel(typeLabel(it))
                    Spacer(Modifier.padding(2.dp))
                }
                filter.categoryId?.let { id ->
                    state.categories.firstOrNull { it.id == id }?.let {
                        FilterChipLabel(it.name)
                        Spacer(Modifier.padding(2.dp))
                    }
                }
                filter.accountId?.let { id ->
                    state.accounts.firstOrNull { it.id == id }?.let {
                        FilterChipLabel(it.name)
                        Spacer(Modifier.padding(2.dp))
                    }
                }
                if (filter.startDate != null || filter.endDate != null) {
                    FilterChipLabel(dateFilterLabel(filter.startDate, filter.endDate))
                    Spacer(Modifier.padding(2.dp))
                }
                TextButton(onClick = viewModel::clearFilter) { Text("清空筛选") }
            }
        }

        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error ?: "读取本地数据失败", onRetry = viewModel::retry)
            state.groups.isEmpty() -> EmptyState(
                message = if (hasFilter) "没有符合筛选条件的流水" else "暂无流水",
                actionLabel = if (hasFilter) null else "记一笔",
                onAction = if (hasFilter) null else {
                    { navController.navigate(Routes.transactionNew()) }
                },
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                state.groups.forEach { group ->
                    stickyHeader(key = "header-${group.date}") {
                        Text(
                            DateTimeFormatters.groupHeader(group.date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                    items(group.items, key = { it.transaction.id }) { detail ->
                        TransactionRowContent(
                            title = transactionTitle(detail),
                            subtitle = transactionSubtitle(detail),
                            amountMinor = detail.transaction.amountMinor,
                            type = detail.transaction.type,
                            timeMillis = detail.transaction.transactionAt,
                            zoneId = ZoneId.systemDefault(),
                            iconKey = detail.categoryIconKey,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    navController.navigate(
                                        Routes.transactionEdit(detail.transaction.id),
                                    )
                                },
                                onLongClick = { deleteTarget = detail.transaction.id },
                            ),
                        )
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            state = state,
            onApply = { filter ->
                viewModel.updateFilter { filter }
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    deleteTarget?.let { targetId ->
        ConfirmDeleteDialog(
            title = "删除这条记录？",
            message = "删除后无法恢复，相关账户余额和统计会立即更新。",
            onConfirm = {
                deleteTarget = null
                viewModel.deleteTransaction(targetId)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    message?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("知道了") }
            },
            text = { Text(text) },
        )
    }
}

@Composable
private fun FilterChipLabel(text: String) {
    AssistChip(onClick = {}, label = { Text(text, style = MaterialTheme.typography.bodySmall) })
}

private fun dateFilterLabel(startDate: LocalDate?, endDate: LocalDate?): String = when {
    startDate != null && endDate != null ->
        "${DateTimeFormatters.date(startDate)} ~ ${DateTimeFormatters.date(endDate)}"
    startDate != null -> "${DateTimeFormatters.date(startDate)} 起"
    endDate != null -> "至 ${DateTimeFormatters.date(endDate)}"
    else -> ""
}

fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: TransactionListUiState,
    onApply: (TransactionListFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(state.filter.type) }
    var categoryId by remember { mutableStateOf(state.filter.categoryId) }
    var accountId by remember { mutableStateOf(state.filter.accountId) }
    var startDate by remember { mutableStateOf(state.filter.startDate) }
    var endDate by remember { mutableStateOf(state.filter.endDate) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("筛选", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Text("类型", style = MaterialTheme.typography.titleSmall)
            Row {
                FilterChip(
                    selected = type == null,
                    onClick = { type = null },
                    label = { Text("全部") },
                )
                Spacer(Modifier.padding(4.dp))
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            type = t
                            if (t == TransactionType.TRANSFER) categoryId = null
                        },
                        label = { Text(typeLabel(t)) },
                    )
                    Spacer(Modifier.padding(4.dp))
                }
            }

            if (type != TransactionType.TRANSFER) {
                Spacer(Modifier.height(8.dp))
                Text("分类", style = MaterialTheme.typography.titleSmall)
                val visibleCategories = state.categories.filter { category ->
                    when (type) {
                        TransactionType.EXPENSE -> category.type == CategoryType.EXPENSE
                        TransactionType.INCOME -> category.type == CategoryType.INCOME
                        else -> true
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        label = { Text("全部") },
                    )
                    Spacer(Modifier.padding(4.dp))
                    visibleCategories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("账户", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = accountId == null,
                    onClick = { accountId = null },
                    label = { Text("全部") },
                )
                Spacer(Modifier.padding(4.dp))
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = accountId == account.id,
                        onClick = { accountId = account.id },
                        label = { Text(account.name) },
                    )
                    Spacer(Modifier.padding(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("日期范围", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickingStart = true }) {
                    Text(startDate?.let { DateTimeFormatters.date(it) } ?: "开始日期")
                }
                Text("至")
                TextButton(onClick = { pickingEnd = true }) {
                    Text(endDate?.let { DateTimeFormatters.date(it) } ?: "结束日期")
                }
                if (startDate != null || endDate != null) {
                    TextButton(
                        onClick = {
                            startDate = null
                            endDate = null
                        },
                    ) { Text("清除") }
                }
            }
            val invalidRange = startDate != null && endDate != null && endDate!!.isBefore(startDate)
            if (invalidRange) {
                Text(
                    "结束日期不能早于开始日期",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onApply(
                        TransactionListFilter(
                            type = type,
                            categoryId = categoryId,
                            accountId = accountId,
                            startDate = startDate,
                            endDate = endDate,
                        ),
                    )
                },
                enabled = !invalidRange,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用筛选") }
        }
    }

    if (pickingStart) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            startDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        pickingStart = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingEnd) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            endDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        pickingEnd = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
