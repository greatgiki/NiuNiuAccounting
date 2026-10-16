package com.example.localledger.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.RatioMath
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.account.accountTypeIcon
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.TransactionRowContent
import com.example.localledger.ui.importing.PendingDraftBanner
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.navigation.navigateToTransactions
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.ui.theme.amountColor
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appSession: AppSession,
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by appSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showLedgerPicker by remember { mutableStateOf(false) }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(state.error ?: "读取本地数据失败", onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showLedgerPicker = true }) {
                        Text(
                            state.ledgerName.ifEmpty { "选择账本" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "切换账本",
                        )
                    }
                }
            }
            item(key = "month") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一月",
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    AnimatedContent(
                        targetState = state.yearMonth,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val enter = slideInVertically { if (forward) it / 2 else -it / 2 } + fadeIn()
                            val exit = slideOutVertically { if (forward) -it / 2 else it / 2 } + fadeOut()
                            enter togetherWith exit
                        },
                        label = "home-month",
                    ) { month ->
                        Text(
                            DateTimeFormatters.yearMonth(month),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一月",
                        )
                    }
                }
            }
            item(key = "drafts") {
                PendingDraftBanner(navController)
            }
            item(key = "summary") {
                MonthOverviewCard(
                    yearMonth = state.yearMonth,
                    incomeMinor = state.incomeMinor,
                    expenseMinor = state.expenseMinor,
                    currencyCode = state.currencyCode,
                )
            }
            item(key = "budget-title") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("本月预算", modifier = Modifier.weight(1f))
                    TextButton(onClick = { navController.navigate(Routes.BUDGETS) }) {
                        Text(if (state.budgets.isEmpty()) "设置" else "管理")
                    }
                }
            }
            if (state.budgets.isEmpty()) {
                item(key = "budget-empty") {
                    EmptyState("还没有预算，可以为这个月设置总预算或分类预算")
                }
            } else {
                items(state.budgets, key = { "budget-${it.id}" }) { line ->
                    BudgetProgressRow(
                        line,
                        state.currencyCode,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "balances-title") {
                SectionTitle("当前账户余额")
            }
            if (state.accountBalances.isEmpty()) {
                item(key = "balances-empty") {
                    EmptyState(
                        message = "暂无账户",
                        actionLabel = "管理账户",
                        onAction = { navController.navigate(Routes.ACCOUNTS) },
                    )
                }
            } else {
                item(key = "balances-card") {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            state.accountBalances.forEach { balance ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { navController.navigate(Routes.ACCOUNTS) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            accountTypeIcon(balance.account.type),
                                            contentDescription = balance.account.name,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Spacer(Modifier.size(12.dp))
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
                            }
                        }
                    }
                }
            }
            item(key = "recent-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("最近流水", modifier = Modifier.weight(1f))
                    TextButton(onClick = { navController.navigateToTransactions() }) {
                        Text("查看全部")
                    }
                }
            }
            if (state.recentTransactions.isEmpty()) {
                item(key = "recent-empty") {
                    EmptyState(
                        message = "还没有流水，点击底部中间记一笔",
                        actionLabel = "记一笔",
                        onAction = { navController.navigate(Routes.transactionNew()) },
                    )
                }
            } else {
                item(key = "recent-card") {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            state.recentTransactions.forEach { detail ->
                                val type = detail.transaction.type
                                TransactionRowContent(
                                    title = transactionTitle(detail),
                                    subtitle = transactionSubtitle(detail, state.currencyCode),
                                    amountMinor = detail.transaction.amountMinor,
                                    type = type,
                                    currencyCode = detail.accountCurrencyCode,
                                    timeMillis = detail.transaction.transactionAt,
                                    zoneId = viewModel.zoneId,
                                    iconKey = detail.categoryIconKey,
                                    modifier = Modifier.clickable {
                                        navController.navigate(
                                            Routes.transactionEdit(detail.transaction.id),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showLedgerPicker) {
        ModalBottomSheet(onDismissRequest = { showLedgerPicker = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "切换账本",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                session.ledgers.filter { !it.isArchived }.forEach { ledger ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    appSession.switchLedger(ledger.id)
                                    showLedgerPicker = false
                                }
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Text(
                            ledger.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (ledger.id == session.currentLedgerId) {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        showLedgerPicker = false
                        navController.navigate(Routes.LEDGERS)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("管理账本")
                }
            }
        }
    }
}

@Composable
fun MonthOverviewCard(
    yearMonth: YearMonth,
    incomeMinor: Long,
    expenseMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                "本月结余",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimary.copy(alpha = 0.86f),
            )
            AnimatedContent(
                targetState = MonthFigures(yearMonth, incomeMinor, expenseMinor),
                transitionSpec = {
                    val forward = targetState.yearMonth >= initialState.yearMonth
                    val enter = slideInVertically(tween(280)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(220))
                    val exit = slideOutVertically(tween(280)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(180))
                    enter togetherWith exit
                },
                label = "month-figures",
            ) { figures ->
                Column {
                    Text(
                        MoneyFormatter.format(figures.incomeMinor - figures.expenseMinor, currencyCode),
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onPrimary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OverviewSecondary(
                            label = "收入",
                            amountMinor = figures.incomeMinor,
                            currencyCode = currencyCode,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewSecondary(
                            label = "支出",
                            amountMinor = figures.expenseMinor,
                            currencyCode = currencyCode,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private data class MonthFigures(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

@Composable
private fun OverviewSecondary(
    label: String,
    amountMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
        Text(
            MoneyFormatter.format(amountMinor, currencyCode),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

fun transactionTitle(detail: com.example.localledger.domain.model.TransactionDetail): String =
    when (detail.transaction.type) {
        TransactionType.TRANSFER -> "${detail.accountName} → ${detail.toAccountName ?: ""}"
        else -> detail.categoryName ?: "未分类"
    }

fun transactionSubtitle(
    detail: com.example.localledger.domain.model.TransactionDetail,
    ledgerCurrency: String = "CNY",
): String = buildList {
    if (detail.transaction.type != TransactionType.TRANSFER) add(detail.accountName)
    val toCurrency = detail.toAccountCurrencyCode
    val toAmount = detail.transaction.toAmountMinor
    if (detail.transaction.type == TransactionType.TRANSFER && toAmount != null &&
        toCurrency != null && toCurrency != detail.accountCurrencyCode
    ) {
        add("转入 ${MoneyFormatter.format(toAmount, toCurrency)}")
    }
    if (detail.accountCurrencyCode != ledgerCurrency) {
        add("折合 ${MoneyFormatter.format(detail.transaction.baseAmountMinor, ledgerCurrency)}")
    }
    detail.transaction.note?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(" · ")

@Composable
fun BudgetProgressRow(
    line: HomeBudgetLine,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val fraction = RatioMath.fraction(line.spentMinor.coerceAtLeast(0), line.limitMinor).coerceIn(0f, 1f)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(line.id) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) fraction else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "budget-progress",
    )
    val barColor = amountColor(if (line.overspent) TransactionType.EXPENSE else TransactionType.INCOME)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val status = if (line.overspent) {
        "超支 ${MoneyFormatter.format(-line.remainingMinor, currencyCode)}"
    } else {
        "剩余 ${MoneyFormatter.format(line.remainingMinor, currencyCode)} / ${MoneyFormatter.format(line.limitMinor, currencyCode)}"
    }
    val description = "${line.label}，$status，已用 ${line.percentLabel}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (line.overspent) "已超支" else line.percentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (line.overspent) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.overspent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(8.dp)
                .semantics { contentDescription = description },
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = track, cornerRadius = radius)
            drawRoundRect(
                color = barColor,
                size = Size(size.width * animated, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MonthOverviewCardPreview() {
    LocalLedgerTheme {
        Column(Modifier.padding(16.dp)) {
            MonthOverviewCard(
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 1_280_000,
                expenseMinor = 436_50,
                currencyCode = "CNY",
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BudgetProgressPreview() {
    LocalLedgerTheme {
        Column(Modifier.padding(16.dp)) {
            BudgetProgressRow(
                line = HomeBudgetLine("food", "餐饮", 10_000, 5_000, 5_000, "50%", false),
                currencyCode = "CNY",
            )
        }
    }
}
