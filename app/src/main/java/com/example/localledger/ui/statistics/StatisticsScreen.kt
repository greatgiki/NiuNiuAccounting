package com.example.localledger.ui.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.AppSession
import com.example.localledger.domain.money.RatioMath
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.common.ChartPalette
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.home.BudgetProgressRow
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.SummaryAmountRow
import com.example.localledger.ui.home.SectionTitle
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.navigation.navigateToTransactions
import com.example.localledger.ui.theme.amountColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId

@Composable
fun StatisticsScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPeriodMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PeriodPicker?>(null) }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(state.error ?: "读取统计数据失败", onRetry = viewModel::retry)
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { showPeriodMenu = true }) {
                    Text(state.periodText, style = MaterialTheme.typography.titleLarge)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择时间范围")
                }
                PeriodMenu(
                    expanded = showPeriodMenu,
                    onDismiss = { showPeriodMenu = false },
                    onPicker = { picker = it },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                SummaryAmountRow(
                    incomeMinor = state.incomeMinor,
                    expenseMinor = state.expenseMinor,
                    modifier = Modifier.padding(16.dp),
                    currencyCode = state.currencyCode,
                )
            }

            if (state.budgets.isNotEmpty()) {
                SectionTitle("本月预算")
                state.budgets.forEach { line ->
                    BudgetProgressRow(line, state.currencyCode)
                }
            }

            SectionTitle("支出分类")
            if (state.categories.isEmpty()) {
                EmptyState("当前时间范围内没有支出")
            } else {
                var expanded by remember { mutableStateOf(false) }
                val shown = if (expanded || state.categories.size <= 8) {
                    state.categories
                } else {
                    state.categories.take(8)
                }
                ExpenseShareChart(
                    categories = state.categories,
                    currencyCode = state.currencyCode,
                )
                ExpenseCategoryBars(
                    categories = shown,
                    totalMinor = state.categories.fold(0L) { acc, item -> Math.addExact(acc, item.amountMinor) },
                    currencyCode = state.currencyCode,
                    useSliceColors = true,
                    onCategoryClick = { categoryId ->
                        navigateToRangeDetail(navController, state.period, categoryId)
                    },
                )
                if (state.categories.size > 8) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开全部（${state.categories.size}）")
                    }
                }
            }

            SectionTitle("收入分类")
            if (state.incomeCategories.isEmpty()) {
                EmptyState("当前时间范围内没有收入")
            } else {
                ExpenseCategoryBars(
                    categories = state.incomeCategories,
                    totalMinor = state.incomeCategories.fold(0L) { acc, item -> Math.addExact(acc, item.amountMinor) },
                    currencyCode = state.currencyCode,
                    barColor = androidx.compose.ui.graphics.Color(0xFF2F6F4E),
                    onCategoryClick = { categoryId ->
                        val (start, end) = periodDates(state.period)
                        navController.navigateToTransactions(
                            type = TransactionType.INCOME,
                            categoryId = categoryId,
                            startDate = start,
                            endDate = end,
                        )
                    },
                )
            }

            if (state.trend.isNotEmpty()) {
                SectionTitle("收支趋势")
                IncomeExpenseTrendChart(
                    points = state.trend,
                    currencyCode = state.currencyCode,
                    onPointClick = { point, type ->
                        navController.navigateToTransactions(
                            type = type,
                            startDate = point.startDate,
                            endDate = point.endDate,
                        )
                    },
                )
            }

            SectionTitle("当前账户余额")
            if (state.accountBalances.isEmpty()) {
                EmptyState("暂无账户")
            } else {
                state.accountBalances.forEach { balance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Routes.ACCOUNTS) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            balance.account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        MoneyText(
                            amountMinor = balance.balanceMinor,
                            currencyCode = balance.account.currencyCode,
                        )
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    picker?.let { selected ->
        PeriodPickerDialog(
            picker = selected,
            current = state.period,
            onDismiss = { picker = null },
            onSelect = { period ->
                viewModel.select(period)
                picker = null
            },
        )
    }
}

private fun periodDates(period: StatisticsPeriod): Pair<LocalDate, LocalDate> = when (period) {
    is StatisticsPeriod.Day -> period.date to period.date
    is StatisticsPeriod.Month -> period.yearMonth.atDay(1) to period.yearMonth.atEndOfMonth()
    is StatisticsPeriod.Year -> LocalDate.of(period.year, 1, 1) to LocalDate.of(period.year, 12, 31)
    is StatisticsPeriod.Custom -> period.startDate to period.endDate
}

private fun navigateToRangeDetail(
    navController: NavHostController,
    period: StatisticsPeriod,
    categoryId: String,
) {
    val (start, end) = when (period) {
        is StatisticsPeriod.Day -> period.date to period.date
        is StatisticsPeriod.Month -> period.yearMonth.atDay(1) to period.yearMonth.atEndOfMonth()
        is StatisticsPeriod.Year -> LocalDate.of(period.year, 1, 1) to LocalDate.of(period.year, 12, 31)
        is StatisticsPeriod.Custom -> period.startDate to period.endDate
    }
    navController.navigateToTransactions(
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        startDate = start,
        endDate = end,
    )
}

@Composable
private fun PeriodMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPicker: (PeriodPicker) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("指定日期") },
            onClick = {
                onPicker(PeriodPicker.DAY)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("指定月份") },
            onClick = {
                onPicker(PeriodPicker.MONTH)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("指定年份") },
            onClick = {
                onPicker(PeriodPicker.YEAR)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("自定义时间段") },
            onClick = {
                onPicker(PeriodPicker.CUSTOM)
                onDismiss()
            },
        )
    }
}

private enum class PeriodPicker { DAY, MONTH, YEAR, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPickerDialog(
    picker: PeriodPicker,
    current: StatisticsPeriod,
    onDismiss: () -> Unit,
    onSelect: (StatisticsPeriod) -> Unit,
) {
    var start by remember { mutableStateOf((current as? StatisticsPeriod.Custom)?.startDate) }
    var end by remember { mutableStateOf((current as? StatisticsPeriod.Custom)?.endDate) }
    var showStart by remember { mutableStateOf(picker == PeriodPicker.DAY || picker == PeriodPicker.CUSTOM) }
    var showEnd by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf(false) }
    var yearText by remember {
        mutableStateOf(
            when (current) {
                is StatisticsPeriod.Year -> current.year.toString()
                is StatisticsPeriod.Month -> current.yearMonth.year.toString()
                else -> LocalDate.now().year.toString()
            },
        )
    }
    var monthText by remember {
        mutableStateOf((current as? StatisticsPeriod.Month)?.yearMonth?.monthValue?.toString() ?: "${LocalDate.now().monthValue}")
    }

    if (showStart || showEnd) {
        val isStart = showStart
        val initial = (if (isStart) start else end) ?: LocalDate.now()
        key(isStart, initial) {
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
            DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val date = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                    if (isStart) {
                        if (picker == PeriodPicker.DAY) onSelect(StatisticsPeriod.Day(date ?: initial))
                        else { start = date; showStart = false; showEnd = true }
                    } else {
                        if (date != null && start != null && date.isBefore(start)) {
                            rangeError = true
                        } else {
                            end = date
                            showEnd = false
                            rangeError = false
                            if (date != null && start != null) {
                                onSelect(StatisticsPeriod.Custom(start!!, date))
                            }
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            ) {
                Column {
                    DatePicker(state = dateState)
                    if (rangeError) {
                        Text(
                            "结束日期不能早于开始日期",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (picker == PeriodPicker.MONTH) "选择月份" else "选择年份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit).take(4) }, label = { Text("年份") }, singleLine = true)
                    if (picker == PeriodPicker.MONTH) OutlinedTextField(value = monthText, onValueChange = { monthText = it.filter(Char::isDigit).take(2) }, label = { Text("月份（1-12）") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val year = yearText.toIntOrNull()
                    val month = monthText.toIntOrNull()
                    if (year != null && year in 1..9999 &&
                        (picker == PeriodPicker.YEAR || month != null && month in 1..12)
                    ) {
                        onSelect(if (picker == PeriodPicker.YEAR) StatisticsPeriod.Year(year) else StatisticsPeriod.Month(YearMonth.of(year, month!!)))
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun ExpenseShareChart(
    categories: List<com.example.localledger.domain.model.CategoryTotal>,
    currencyCode: String,
) {
    val total = categories.fold(0L) { acc, item -> Math.addExact(acc, item.amountMinor) }
    val slices = if (categories.size <= 8) {
        categories.map { it.categoryName to it.amountMinor }
    } else {
        val rest = categories.drop(7).fold(0L) { acc, item -> Math.addExact(acc, item.amountMinor) }
        categories.take(7).map { it.categoryName to it.amountMinor } + ("其他" to rest)
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700))
    }
    val totalLabel = "总支出 ${MoneyFormatter.format(total, currencyCode)}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = "支出分类占比，共${slices.size}项，$totalLabel"
                },
            ) {
                val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                val inset = stroke.width / 2f
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = Offset(inset, inset)
                if (total <= 0L) {
                    drawArc(
                        color = ChartPalette.color(7),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                } else {
                    var start = -90f
                    slices.forEachIndexed { index, (_, amount) ->
                        val fullSweep = RatioMath.fraction(amount, total) * 360f
                        drawArc(
                            color = ChartPalette.color(index),
                            startAngle = start,
                            sweepAngle = fullSweep * progress.value,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        start += fullSweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "总支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    MoneyFormatter.format(total, currencyCode),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            slices.forEachIndexed { index, (name, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(ChartPalette.color(index)) }
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        "${RatioMath.percentLabel(amount, total)} ${MoneyFormatter.format(amount, currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpenseCategoryBars(
    categories: List<com.example.localledger.domain.model.CategoryTotal>,
    totalMinor: Long,
    currencyCode: String,
    barColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFC4392F),
    useSliceColors: Boolean = false,
    onCategoryClick: (String) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(categories, totalMinor) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        categories.forEachIndexed { index, category ->
            val fraction = RatioMath.fraction(category.amountMinor, totalMinor)
            val slice = if (useSliceColors) ChartPalette.color(index) else barColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category.categoryId) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(slice.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.resolve(category.iconKey).image,
                        contentDescription = category.categoryName,
                        modifier = Modifier.size(22.dp),
                        tint = slice,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            category.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            RatioMath.percentLabel(category.amountMinor, totalMinor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        MoneyText(
                            amountMinor = category.amountMinor,
                            style = MaterialTheme.typography.bodyMedium,
                            currencyCode = currencyCode,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(color = track, cornerRadius = radius)
                        drawRoundRect(
                            color = slice,
                            size = Size(size.width * fraction * progress.value, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeExpenseTrendChart(
    points: List<TrendPoint>,
    currencyCode: String,
    onPointClick: (TrendPoint, TransactionType) -> Unit,
) {
    val incomeColor = amountColor(TransactionType.INCOME)
    val expenseColor = amountColor(TransactionType.EXPENSE)
    val maxValue = points.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(1)
    val description = "收支趋势图，共${points.size}个时间点，可横向滑动，点击查看对应明细"
    val scrollable = points.size > 8
    var selected by remember { mutableStateOf<TrendSelection?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700))
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem("收入", incomeColor)
            LegendItem("支出", expenseColor)
            Spacer(Modifier.weight(1f))
            Text(
                "最高 ${MoneyFormatter.format(maxValue, currencyCode)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        selected?.let { current ->
            val point = points.getOrNull(current.index)
            if (point != null) {
                val value = if (current.type == TransactionType.INCOME) point.incomeMinor else point.expenseMinor
                val typeName = if (current.type == TransactionType.INCOME) "收入" else "支出"
                Surface(
                    onClick = { onPointClick(point, current.type) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text(
                        "${point.label} $typeName ${MoneyFormatter.format(value, currencyCode)} · 查看明细",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .align(Alignment.TopStart),
            ) {
                val lines = 3
                repeat(lines) { index ->
                    val y = if (lines == 1) 0f else size.height * index / (lines - 1)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
                    .height(160.dp)
                    .semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                points.forEachIndexed { index, point ->
                    Column(
                        modifier = if (scrollable) Modifier.width(36.dp) else Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            TrendBar(point.incomeMinor, maxValue, incomeColor, progress.value) {
                                selected = TrendSelection(index, TransactionType.INCOME)
                            }
                            TrendBar(point.expenseMinor, maxValue, expenseColor, progress.value) {
                                selected = TrendSelection(index, TransactionType.EXPENSE)
                            }
                        }
                        if (points.size <= 12 || point.label.endsWith("1日") ||
                            points.indexOf(point) % (points.size / 8 + 1) == 0
                        ) {
                            Text(
                                point.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        } else {
                            Text("", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private data class TrendSelection(val index: Int, val type: TransactionType)

@Composable
private fun TrendBar(
    value: Long,
    maxValue: Long,
    color: androidx.compose.ui.graphics.Color,
    progress: Float,
    onClick: () -> Unit,
) {
    val fraction = RatioMath.fraction(value, maxValue) * progress
    Canvas(
        modifier = Modifier
            .width(8.dp)
            .height(120.dp)
            .clickable(onClick = onClick),
    ) {
        val barHeight = (size.height * fraction).coerceAtLeast(if (value > 0) 2f else 0f)
        val radius = CornerRadius(size.width / 2f, size.width / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(size.width, barHeight),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun LegendItem(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRect(color = color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
