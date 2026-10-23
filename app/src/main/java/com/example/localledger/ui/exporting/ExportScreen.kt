package com.example.localledger.ui.exporting

import com.example.localledger.ui.component.LedgerDatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.localledger.data.export.SpreadsheetFormat
import com.example.localledger.data.export.TransactionExportRepository
import com.example.localledger.domain.time.ZoneIdProvider
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.component.LedgerCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@Immutable
data class LedgerOption(val id: String, val name: String)

@Immutable
data class ExportUiState(
    val ledgers: List<LedgerOption> = emptyList(),
    val ledgerId: String = "",
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now(),
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: TransactionExportRepository,
    private val appSession: AppSession,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutableState.asStateFlow()
    private var pendingFormat: SpreadsheetFormat = SpreadsheetFormat.CSV

    init {
        val zone = zoneIdProvider.current()
        val today = LocalDate.now(zone)
        mutableState.update { it.copy(start = YearMonth.from(today).atDay(1), end = today) }
        viewModelScope.launch {
            appSession.state.collect { session ->
                val options = session.ledgers.map { LedgerOption(it.id, it.name) }
                val current = session.currentLedgerId
                mutableState.update { state ->
                    val selected = when {
                        options.any { it.id == state.ledgerId } -> state.ledgerId
                        current != null && options.any { it.id == current } -> current
                        else -> options.firstOrNull()?.id.orEmpty()
                    }
                    state.copy(ledgers = options, ledgerId = selected)
                }
            }
        }
    }

    fun selectLedger(id: String) = mutableState.update { it.copy(ledgerId = id, message = null) }

    fun selectStart(date: LocalDate) = mutableState.update { it.copy(start = date, message = null) }

    fun selectEnd(date: LocalDate) = mutableState.update { it.copy(end = date, message = null) }

    fun beginExport(format: SpreadsheetFormat) {
        pendingFormat = format
    }

    fun write(uri: android.net.Uri) {
        val snapshot = mutableState.value
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null, isError = false) }
            val result = runCatching {
                repository.write(uri, pendingFormat, snapshot.ledgerId, snapshot.start, snapshot.end)
            }
            if (result.exceptionOrNull() is CancellationException) return@launch
            mutableState.update {
                it.copy(
                    busy = false,
                    isError = result.isFailure,
                    message = result.fold(
                        onSuccess = { count -> "已导出 $count 笔流水" },
                        onFailure = { error -> error.message ?: "导出失败，请重试" },
                    ),
                )
            }
        }
    }

    fun suggestedName(format: SpreadsheetFormat): String {
        val state = mutableState.value
        val stamp = DateTimeFormatter.BASIC_ISO_DATE
        val extension = if (format == SpreadsheetFormat.CSV) "csv" else "xlsx"
        return "牛牛记账流水-${state.start.format(stamp)}-${state.end.format(stamp)}.$extension"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(navController: NavHostController, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.write(uri)
    }
    val createXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null) viewModel.write(uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出流水") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        ExportContent(
            state = state,
            onLedger = viewModel::selectLedger,
            onStart = viewModel::selectStart,
            onEnd = viewModel::selectEnd,
            onCsv = {
                viewModel.beginExport(SpreadsheetFormat.CSV)
                createCsv.launch(viewModel.suggestedName(SpreadsheetFormat.CSV))
            },
            onXlsx = {
                viewModel.beginExport(SpreadsheetFormat.XLSX)
                createXlsx.launch(viewModel.suggestedName(SpreadsheetFormat.XLSX))
            },
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportContent(
    state: ExportUiState,
    onLedger: (String) -> Unit,
    onStart: (LocalDate) -> Unit,
    onEnd: (LocalDate) -> Unit,
    onCsv: () -> Unit,
    onXlsx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("选择账本和日期", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "导出这一段里的流水。CSV 使用 UTF-8 并带有 BOM，方便用 Excel 打开中文。Excel 文件按表格格式直接写成压缩包，不打包体积很大的表格库。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LedgerMenu(state.ledgers, state.ledgerId, !state.busy, onLedger)
                Spacer(Modifier.height(8.dp))
                DateField("开始", state.start, !state.busy, onStart)
                Spacer(Modifier.height(8.dp))
                DateField("结束", state.end, !state.busy, onEnd)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onXlsx,
                    enabled = !state.busy && state.ledgerId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (state.busy) "正在导出…" else "导出 Excel") }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCsv,
                    enabled = !state.busy && state.ledgerId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("导出 CSV") }
            }
        }
        state.message?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(top = 16.dp),
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerMenu(
    ledgers: List<LedgerOption>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = ledgers.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "请选择账本",
            onValueChange = {},
            readOnly = true,
            label = { Text("账本") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ledgers.forEach { ledger ->
                DropdownMenuItem(
                    text = { Text(ledger.name) },
                    onClick = {
                        onSelect(ledger.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, enabled: Boolean, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        OutlinedTextField(
            value = DateTimeFormatters.date(date),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, onClick = { open = true })
                .semantics { contentDescription = "选择$label" },
        )
    }
    if (open) {
        val initial = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        LedgerDatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        ) { DatePicker(state = picker) }
    }
}
