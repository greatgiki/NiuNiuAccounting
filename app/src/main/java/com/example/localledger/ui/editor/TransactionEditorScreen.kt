package com.example.localledger.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.component.AmountField
import com.example.localledger.ui.component.CategoryIconGrid
import com.example.localledger.ui.component.ConfirmDeleteDialog
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.ui.theme.amountColor
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditorEffect.NavigateBack -> navController.popBackStack()
                is EditorEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除记录")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(modifier = Modifier.padding(padding)) { LoadingState() }
            state.input.notFound -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("记录不存在或已删除")
            }
            else -> EditorContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "删除这条记录？",
            message = "删除后无法恢复，相关账户余额和统计会立即更新。",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    viewModel: TransactionEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val input = state.input
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val typeColor = amountColor(input.type)

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TransactionType.entries.forEachIndexed { index, type ->
                val active = amountColor(type)
                SegmentedButton(
                    selected = input.type == type,
                    onClick = { viewModel.onTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TransactionType.entries.size,
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = active,
                        activeContentColor = Color.White,
                        activeBorderColor = active,
                    ),
                ) {
                    Text(
                        when (type) {
                            TransactionType.EXPENSE -> "支出"
                            TransactionType.INCOME -> "收入"
                            TransactionType.TRANSFER -> "转账"
                        },
                    )
                }
            }
        }

        val sourceCurrency = state.allAccounts.firstOrNull { it.id == input.accountId }?.currencyCode ?: "CNY"
        val sourceSymbol = CurrencyCatalog.symbol(sourceCurrency)
        Text(
            text = if (input.amountText.isEmpty()) "${sourceSymbol}0" else "$sourceSymbol${input.amountText}",
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
            color = if (input.amountError != null) MaterialTheme.colorScheme.error else typeColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (input.amountError != null) {
            Text(
                input.amountError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (input.type != TransactionType.TRANSFER) {
                Text("分类", style = MaterialTheme.typography.titleMedium)
                if (input.categoryError != null) {
                    Text(
                        input.categoryError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                CategoryIconGrid(
                    categories = state.activeCategories,
                    selectedId = input.categoryId,
                    onSelect = viewModel::onCategorySelect,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (input.type == TransactionType.TRANSFER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountSelector(
                        label = "转出账户",
                        accounts = state.activeAccounts,
                        allAccounts = state.allAccounts,
                        selectedId = input.accountId,
                        onSelect = viewModel::onAccountSelect,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::swapTransferAccounts) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "交换账户")
                    }
                    AccountSelector(
                        label = "转入账户",
                        accounts = state.activeAccounts,
                        allAccounts = state.allAccounts,
                        selectedId = input.toAccountId,
                        onSelect = viewModel::onToAccountSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                AccountSelector(
                    label = "账户",
                    accounts = state.activeAccounts,
                    allAccounts = state.allAccounts,
                    selectedId = input.accountId,
                    onSelect = viewModel::onAccountSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val destinationCurrency = state.allAccounts.firstOrNull { it.id == input.toAccountId }?.currencyCode
            if (input.type == TransactionType.TRANSFER && destinationCurrency != null && destinationCurrency != sourceCurrency) {
                Spacer(Modifier.height(8.dp))
                AmountField(
                    value = input.toAmountText,
                    onValueChange = viewModel::onToAmountChange,
                    label = "转入金额（留空按最近汇率）",
                    currencyCode = destinationCurrency,
                )
            }
            if (input.accountError != null) {
                Text(
                    input.accountError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        DateTimeFormatters.date(
                            Instant.ofEpochMilli(input.transactionAt)
                                .atZone(ZoneId.systemDefault()).toLocalDate(),
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        DateTimeFormatters.time(input.transactionAt, ZoneId.systemDefault()),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = input.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
            Spacer(Modifier.height(12.dp))
        }

        AmountKeypad(
            onKey = viewModel::onAmountKey,
            onSave = viewModel::save,
            saveEnabled = state.canSave && !input.saving,
            saveLabel = when {
                input.saving -> "…"
                state.isEdit -> "完成"
                else -> "保存"
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(input.transactionAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC")).toLocalDate()
                            viewModel.onDateChange(date)
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val current = Instant.ofEpochMilli(input.transactionAt)
            .atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
internal fun AmountKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    saveLabel: String = "保存",
) {
    val keyHeight = 44.dp
    val gap = 6.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            listOf("1", "2", "3").forEach { key ->
                KeypadKey(key, onClick = { onKey(key) }, modifier = Modifier.weight(1f).height(keyHeight))
            }
            KeypadKey(
                label = "",
                onClick = { onKey("backspace") },
                modifier = Modifier.weight(1f).height(keyHeight),
                icon = {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "退格")
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            listOf("4", "5", "6").forEach { key ->
                KeypadKey(key, onClick = { onKey(key) }, modifier = Modifier.weight(1f).height(keyHeight))
            }
            KeypadKey("清空", onClick = { onKey("clear") }, modifier = Modifier.weight(1f).height(keyHeight))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(keyHeight * 2 + gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Column(
                modifier = Modifier.weight(3f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f)) {
                    listOf("7", "8", "9").forEach { key ->
                        KeypadKey(key, onClick = { onKey(key) }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f)) {
                    KeypadKey(".", onClick = { onKey(".") }, modifier = Modifier.weight(1f).fillMaxHeight())
                    KeypadKey("0", onClick = { onKey("0") }, modifier = Modifier.weight(2f).fillMaxHeight())
                }
            }
            KeypadKey(
                label = saveLabel,
                onClick = { onSave?.invoke() },
                enabled = onSave != null && saveEnabled,
                container = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, label = "key-press")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) container else container.copy(alpha = 0.45f),
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier.scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                icon()
            } else {
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun AccountSelector(
    label: String,
    accounts: List<Account>,
    allAccounts: List<Account>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = allAccounts.firstOrNull { it.id == selectedId }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "$label：${selected?.name ?: "请选择"}",
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AmountKeypadPreview() {
    LocalLedgerTheme {
        AmountKeypad(onKey = {}, onSave = {}, modifier = Modifier.padding(12.dp))
    }
}
