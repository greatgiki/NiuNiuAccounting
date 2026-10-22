package com.example.localledger.ui.report

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.report.ReportKind
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LedgerSnackbarHost
import com.example.localledger.ui.component.LoadingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodReportScreen(
    navController: NavHostController,
    viewModel: PeriodReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val capture = remember { ReportCaptureController() }
    val layer = rememberReportLayer()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val title = if (state.kind == ReportKind.WEEK) "周报" else "月报"
    val createPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = captureBitmap(capture, layer, viewModel, snackbar) ?: return@launch
            val wrote = runCatching {
                withContext(Dispatchers.IO) { ReportFiles.writePng(context, uri, bitmap) }
            }
            bitmap.recycle()
            snackbar.showSnackbar(if (wrote.isSuccess) "已保存图片" else "没有保存图片")
        }
    }

    fun guarded(block: suspend () -> Unit) {
        if (busy) return
        val reason = viewModel.shareBlockReason()
        if (reason != null) {
            scope.launch { snackbar.showSnackbar(reason) }
            return
        }
        busy = true
        scope.launch {
            try {
                block()
            } catch (cancel: CancellationException) {
                if (cancel is TimeoutCancellationException) snackbar.showSnackbar("没有生成图片") else throw cancel
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        snackbarHost = { LedgerSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.report != null && !state.locked) {
                        IconButton(onClick = { menu = true }, enabled = !busy) {
                            Icon(Icons.Outlined.Share, contentDescription = "分享报表")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("分享图片") },
                                onClick = {
                                    menu = false
                                    guarded {
                                        val bitmap = captureBitmap(capture, layer, viewModel, snackbar) ?: return@guarded
                                        val text = viewModel.uiState.value.report?.shareText.orEmpty()
                                        runCatching { ReportFiles.sharePng(context, bitmap, text) }
                                            .onFailure { snackbar.showSnackbar("没有完成分享") }
                                        bitmap.recycle()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享文字") },
                                onClick = {
                                    menu = false
                                    guarded {
                                        val reason = viewModel.shareBlockReason()
                                        if (reason != null) {
                                            snackbar.showSnackbar(reason)
                                            return@guarded
                                        }
                                        val text = viewModel.uiState.value.report?.shareText ?: return@guarded
                                        runCatching { ReportFiles.shareText(context, text) }
                                            .onFailure { snackbar.showSnackbar("没有完成分享") }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("保存图片") },
                                onClick = {
                                    menu = false
                                    val reason = viewModel.shareBlockReason()
                                    if (reason != null) {
                                        scope.launch { snackbar.showSnackbar(reason) }
                                        return@DropdownMenuItem
                                    }
                                    val report = state.report ?: return@DropdownMenuItem
                                    createPng.launch(ReportFiles.suggestedName(report.kind, report.startDate))
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.locked -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("请先解锁后再查看报表", style = MaterialTheme.typography.bodyLarge)
            }
            state.error != null && state.report == null -> Box(Modifier.padding(padding)) {
                ErrorState(state.error ?: "读取报表失败", onRetry = viewModel::retry)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                PeriodSwitcher(
                    label = state.periodLabel.ifBlank { title },
                    kind = state.kind,
                    canPrevious = state.canPrevious,
                    canNext = state.canNext,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onPick = { picking = true },
                )
                when {
                    state.loading && state.report == null -> LoadingState()
                    state.report == null -> EmptyState(state.emptyMessage ?: "暂时没有报表")
                    else -> ReportCaptureHost(
                        controller = capture,
                        layer = layer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(top = 4.dp, bottom = 24.dp),
                        ) {
                            ReportCard(state.report!!)
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        if (state.kind == ReportKind.WEEK) {
            WeekPicker(
                initial = state.anchor,
                onDismiss = { picking = false },
                onSelect = {
                    viewModel.jump(it)
                    picking = false
                },
            )
        } else {
            MonthPicker(
                initial = state.anchor,
                onDismiss = { picking = false },
                onSelect = {
                    viewModel.jump(it)
                    picking = false
                },
            )
        }
    }
}

private suspend fun captureBitmap(
    capture: ReportCaptureController,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    viewModel: PeriodReportViewModel,
    snackbar: SnackbarHostState,
): Bitmap? {
    val reason = viewModel.shareBlockReason()
    if (reason != null) {
        snackbar.showSnackbar(reason)
        return null
    }
    val bitmap = try {
        capture.capture(layer)
    } catch (cancel: CancellationException) {
        if (cancel is TimeoutCancellationException) {
            snackbar.showSnackbar("没有生成图片")
            return null
        }
        throw cancel
    }
    val after = viewModel.shareBlockReason()
    if (after != null) {
        bitmap.recycle()
        snackbar.showSnackbar(after)
        return null
    }
    return bitmap
}

@Composable
private fun PeriodSwitcher(
    label: String,
    kind: ReportKind,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    val previous = if (kind == ReportKind.WEEK) "上一周" else "上一月"
    val next = if (kind == ReportKind.WEEK) "下一周" else "下一月"
    val pick = if (kind == ReportKind.WEEK) "选择周" else "选择月份"
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = canPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = previous)
        }
        TextButton(
            onClick = onPick,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = pick },
            )
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = next)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    key(initial) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val picked = dateState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    } ?: initial
                    onSelect(picked)
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun MonthPicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    var yearText by remember { mutableStateOf(initial.year.toString()) }
    var monthText by remember { mutableStateOf(initial.monthValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                    label = { Text("年份") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = monthText,
                    onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                    label = { Text("月份（1-12）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val year = yearText.toIntOrNull()
                val month = monthText.toIntOrNull()
                if (year != null && year in 1..9999 && month != null && month in 1..12) {
                    onSelect(LocalDate.of(year, month, 1))
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
