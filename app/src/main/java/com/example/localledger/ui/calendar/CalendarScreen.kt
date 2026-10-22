package com.example.localledger.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.calendar.CalendarCell
import com.example.localledger.domain.calendar.CalendarGrid
import com.example.localledger.domain.calendar.CalendarPager
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.TransactionRowContent
import com.example.localledger.ui.home.transactionSubtitle
import com.example.localledger.ui.home.transactionTitle
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.theme.LedgerMotion
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.amountColorFor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val Weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val todayMonth = remember(state.today) { YearMonth.from(state.today) }
    val pageCount = remember(todayMonth) { CalendarPager.pageCount(todayMonth) }
    val pagerState = rememberPagerState(
        initialPage = CalendarPager.pageOf(todayMonth, todayMonth),
        pageCount = { pageCount },
    )
    LaunchedEffect(pagerState, state.ledgerId) {
        snapshotFlow { pagerState.settledPage }
            .map { CalendarPager.monthOf(it) }
            .distinctUntilChanged()
            .collect { month ->
                viewModel.onVisibleMonth(month)
                viewModel.ensureMonth(month.minusMonths(1))
                viewModel.ensureMonth(month)
                viewModel.ensureMonth(month.plusMonths(1))
            }
    }
    val selectDate = remember(viewModel, pagerState, scope, todayMonth) {
        { date: LocalDate ->
            viewModel.select(date)
            val page = CalendarPager.pageOf(YearMonth.from(date), todayMonth)
            if (pagerState.currentPage != page) {
                scope.launch {
                    pagerState.animateScrollToPage(page, animationSpec = LedgerMotion.medium())
                }
            }
        }
    }
    val visibleMonth = CalendarPager.monthOf(pagerState.currentPage)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text("日历", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        },
    ) { padding ->
        CalendarContent(
            visibleMonth = visibleMonth,
            pagerState = pagerState,
            months = state.months,
            selected = state.selected,
            today = state.today,
            currencyCode = state.currencyCode,
            dayRows = state.dayRows,
            dayReady = state.dayReady,
            onPrevious = {
                val page = (pagerState.currentPage - 1).coerceAtLeast(0)
                scope.launch { pagerState.animateScrollToPage(page, animationSpec = LedgerMotion.medium()) }
            },
            onNext = {
                val page = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                scope.launch { pagerState.animateScrollToPage(page, animationSpec = LedgerMotion.medium()) }
            },
            onSelect = selectDate,
            onOpen = { id -> navController.navigate(Routes.transactionEdit(id)) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun CalendarContent(
    visibleMonth: YearMonth,
    months: Map<YearMonth, Map<LocalDate, IncomeExpenseSummary>>,
    selected: LocalDate,
    today: LocalDate,
    currencyCode: String,
    dayRows: List<TransactionDetail>,
    dayReady: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: androidx.compose.foundation.pager.PagerState? = null,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MonthTitle(visibleMonth, onPrevious, onNext)
        WeekdayHeader()
        if (pagerState == null) {
            MonthGrid(
                month = visibleMonth,
                totals = months[visibleMonth].orEmpty(),
                selected = selected,
                today = today,
                onSelect = onSelect,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
                beyondViewportPageCount = 1,
                key = { CalendarPager.monthOf(it).toString() },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = LedgerMotion.medium(),
                ),
            ) { page ->
                val month = CalendarPager.monthOf(page)
                MonthGrid(
                    month = month,
                    totals = months[month].orEmpty(),
                    selected = selected,
                    today = today,
                    onSelect = onSelect,
                )
            }
        }
        DaySection(
            selected = selected,
            totals = months[YearMonth.from(selected)].orEmpty()[selected],
            currencyCode = currencyCode,
            rows = dayRows,
            ready = dayReady,
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}

private val GRID_HEIGHT = (6 * 52).dp

@Composable
private fun MonthTitle(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一月")
        }
        Text(
            DateTimeFormatters.yearMonth(month),
            modifier = Modifier.testTag("calendar-month"),
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一月")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Weekdays.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    totals: Map<LocalDate, IncomeExpenseSummary>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month, totals) { CalendarGrid.cells(month, totals) }
    Column(modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT).padding(horizontal = 8.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        selected = cell.date == selected,
                        today = cell.date == today,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarCell,
    selected: Boolean,
    today: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    val border = if (today && !selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
    val dayColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        cell.inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .padding(1.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(if (today && !selected) 0.5.dp else 0.dp, border, shape)
            .clickable { onSelect(cell.date) }
            .semantics { this.selected = selected }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            color = dayColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (cell.inMonth) FontWeight.Medium else FontWeight.Normal,
                fontFeatureSettings = "tnum",
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
        )
        AmountHint(cell.incomeMinor, income = true)
        AmountHint(cell.expenseMinor, income = false)
    }
}

@Composable
private fun AmountHint(minor: Long, income: Boolean) {
    val dark = LocalLedgerDarkTheme.current
    val text = CalendarGrid.compactYuan(minor)
    Text(
        text.ifEmpty { " " },
        color = if (text.isEmpty()) {
            MaterialTheme.colorScheme.background
        } else {
            amountColorFor(dark, if (income) TransactionType.INCOME else TransactionType.EXPENSE)
        },
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum", letterSpacing = 0.sp, fontSize = 10.sp),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun DaySection(
    selected: LocalDate,
    totals: IncomeExpenseSummary?,
    currencyCode: String,
    rows: List<TransactionDetail>,
    ready: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val income = totals?.incomeMinor ?: 0L
    val expense = totals?.expenseMinor ?: 0L
    Column(modifier) {
        Text(
            DateTimeFormatters.date(selected),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "收入 ${MoneyFormatter.format(income, currencyCode)}    支出 ${MoneyFormatter.format(expense, currencyCode)}",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        when {
            !ready -> Box(Modifier.fillMaxWidth().height(48.dp))
            rows.isEmpty() -> EmptyState("这一天还没有流水")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("calendar-day"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(rows, key = { it.transaction.id }, contentType = { "calendar-tx" }) { detail ->
                    val id = detail.transaction.id
                    val open = remember(id) { { onOpen(id) } }
                    TransactionRowContent(
                        title = transactionTitle(detail),
                        subtitle = daySubtitle(detail, currencyCode),
                        amountMinor = detail.transaction.amountMinor,
                        currencyCode = detail.accountCurrencyCode,
                        type = detail.transaction.type,
                        timeMillis = detail.transaction.transactionAt,
                        zoneId = java.time.ZoneId.systemDefault(),
                        iconKey = detail.categoryIconKey,
                        modifier = Modifier.clickable(onClick = open),
                    )
                }
            }
        }
    }
}

private fun daySubtitle(detail: TransactionDetail, currencyCode: String): String {
    val party = detail.transaction.counterparty?.takeIf { it.isNotBlank() }
    val base = transactionSubtitle(detail, currencyCode)
    return if (party.isNullOrBlank() || base.contains(party)) base else "$party · $base"
}
