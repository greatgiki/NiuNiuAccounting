package com.example.localledger.ui.statistics

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.AppSession
import com.example.localledger.domain.money.RatioMath
import com.example.localledger.ui.common.CategoryIcons
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
    val colors = listOf(
        androidx.compose.ui.graphics.Color(0xFFC4392F),
        androidx.compose.ui.graphics.Color(0xFFD97706),
        androidx.compose.ui.graphics.Color(0xFF2F6F4E),
        androidx.compose.ui.graphics.Color(0xFF2563EB),
        androidx.compose.ui.graphics.Color(0xFF7C3AED),
        androidx.compose.ui.graphics.Color(0xFF0F766E),
        androidx.compose.ui.graphics.Color(0xFFBE185D),
        androidx.compose.ui.graphics.Color(0xFF64748B),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier.size(112.dp).semantics {
                contentDescription = "支出分类占比，共${slices.size}项"
            },
        ) {
            var start = -90f
            slices.forEachIndexed { index, (_, amount) ->
                val sweep = RatioMath.fraction(amount, total) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            slices.forEachIndexed { index, (name, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawRect(colors[index % colors.size]) }
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
    onCategoryClick: (String) -> Unit,
) {
    Column {
        categories.forEach { category ->
            val fraction = RatioMath.fraction(category.amountMinor, totalMinor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category.categoryId) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    CategoryIcons.resolve(category.iconKey).image,
                    contentDescription = category.categoryName,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                        drawRect(
                            color = barColor,
                            size = Size(size.width * fraction, size.height),
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
    onPointClick: (TrendPoint, TransactionType) -> Unit,
) {
    val incomeColor = amountColor(TransactionType.INCOME)
    val expenseColor = amountColor(TransactionType.EXPENSE)
    val maxValue = points.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(1)
    val description = "收支趋势图，共${points.size}个时间点，可横向滑动，点击查看对应明细"
    val scrollable = points.size > 8

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            LegendItem("收入", incomeColor)
            LegendItem("支出", expenseColor)
        }
        Row(
            modifier = Modifier
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
                .height(160.dp)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
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
                        TrendBar(point.incomeMinor, maxValue, incomeColor) { onPointClick(point, TransactionType.INCOME) }
                        TrendBar(point.expenseMinor, maxValue, expenseColor) { onPointClick(point, TransactionType.EXPENSE) }
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

@Composable
private fun TrendBar(value: Long, maxValue: Long, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val fraction = RatioMath.fraction(value, maxValue)
    Canvas(
            modifier = Modifier
                .width(6.dp)
                .height(120.dp)
                .clickable(onClick = onClick),
    ) {
        val barHeight = (size.height * fraction).coerceAtLeast(if (value > 0) 2f else 0f)
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(size.width, barHeight),
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
