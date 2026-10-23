package com.example.localledger.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
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
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.TransactionRowContent
import com.example.localledger.ui.importing.PendingDraftBanner
import com.example.localledger.ui.navigation.LocalSelectTopLevelPage
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.navigation.TopLevelPage
import com.example.localledger.ui.navigation.claimPagerHorizontalGesture
import com.example.localledger.ui.theme.LedgerMotion
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.ui.theme.LocalLedgerTones
import com.example.localledger.ui.theme.amountColor
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val selectTopLevelPage = LocalSelectTopLevelPage.current
    var showLedgerPicker by remember { mutableStateOf(false) }
    val openLedgerPicker = remember { { showLedgerPicker = true } }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(state.error ?: "读取本地数据失败", onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .homeSafeInsets(),
            contentPadding = HomeListPadding,
        ) {
            item(key = "top", contentType = "home-top") {
                HomeTopSection(
                    ledgerName = state.ledgerName,
                    yearMonth = state.yearMonth,
                    incomeMinor = state.incomeMinor,
                    expenseMinor = state.expenseMinor,
                    currencyCode = state.currencyCode,
                    onLedgerClick = openLedgerPicker,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                )
            }
            item(key = "calendar", contentType = "calendar") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { navController.navigate(Routes.CALENDAR) }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("日历")
                    }
                    TextButton(
                        onClick = {
                            navController.navigate(Routes.weekReport(LocalDate.now(viewModel.zoneId)))
                        },
                        modifier = Modifier.semantics { contentDescription = "生成本周周报" },
                    ) {
                        Text("周报")
                    }
                    TextButton(
                        onClick = { navController.navigate(Routes.monthReport(state.yearMonth.atDay(1))) },
                    ) {
                        Text("月报")
                    }
                    TextButton(onClick = { navController.navigate(Routes.ASK) }) {
                        Text("问账本")
                    }
                }
            }
            item(key = "drafts", contentType = "drafts") {
                PendingDraftBanner(
                    navController,
                    modifier = Modifier.padding(top = 8.dp),
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
                    LedgerCard(
                        modifier = Modifier.fillMaxWidth().animateItem(),
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
                    TextButton(onClick = { selectTopLevelPage(TopLevelPage.Transactions) }) {
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
                    LedgerCard(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            state.recentTransactions.forEachIndexed { index, detail ->
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
                                    appearIndex = index,
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

internal val HomeListPadding = PaddingValues(
    start = 16.dp,
    top = 16.dp,
    end = 16.dp,
    bottom = 24.dp,
)

/** Status bar and display cutout still left after the scaffold padding is consumed. */
@Composable
internal fun Modifier.homeSafeInsets(
    insets: WindowInsets = WindowInsets.safeDrawing,
): Modifier = this.windowInsetsPadding(
    insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
)

@Composable
internal fun HomeTopSection(
    ledgerName: String,
    yearMonth: YearMonth,
    incomeMinor: Long,
    expenseMinor: Long,
    currencyCode: String,
    onLedgerClick: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        LedgerSelector(ledgerName = ledgerName, onClick = onLedgerClick)
        Spacer(Modifier.height(16.dp))
        MonthOverviewCard(
            yearMonth = yearMonth,
            incomeMinor = incomeMinor,
            expenseMinor = expenseMinor,
            currencyCode = currencyCode,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )
    }
}

@Composable
private fun LedgerSelector(ledgerName: String, onClick: () -> Unit) {
    val shown = ledgerName.ifEmpty { "选择账本" }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .testTag("home-ledger")
                .widthIn(max = maxWidth)
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick, role = Role.Button, onClickLabel = "切换账本"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shown,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                tint = color,
            )
        }
    }
}

@Composable
fun MonthOverviewCard(
    yearMonth: YearMonth,
    incomeMinor: Long,
    expenseMinor: Long,
    currencyCode: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = LocalLedgerTones.current
    val shape = MaterialTheme.shapes.extraLarge
    val heroBrush = remember(tones.heroTop, tones.heroBottom) {
        Brush.verticalGradient(listOf(tones.heroTop, tones.heroBottom))
    }
    val border = remember(tones.accent) { tones.accent.copy(alpha = 0.45f) }
    val rule = remember(tones.accent) { tones.accent.copy(alpha = 0.32f) }
    val figures = remember(yearMonth, incomeMinor, expenseMinor) {
        MonthFigures(yearMonth, incomeMinor, expenseMinor)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.5.dp, border, shape)
            .background(heroBrush),
    ) {
        MonthSwitcher(
            yearMonth = yearMonth,
            ink = tones.heroInk,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )
        MonthFiguresBlock(
            figures = figures,
            currencyCode = currencyCode,
            rule = rule,
        )
    }
}

@Composable
private fun MonthSwitcher(
    yearMonth: YearMonth,
    ink: Color,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = ink)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
            .claimPagerHorizontalGesture(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousMonth, colors = iconColors) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "上一月",
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedContent(
            targetState = yearMonth,
            modifier = Modifier.widthIn(min = 144.dp),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInVertically(LedgerMotion.medium()) { if (forward) it / 2 else -it / 2 } +
                    fadeIn(LedgerMotion.short())
                val exit = slideOutVertically(LedgerMotion.medium()) { if (forward) -it / 2 else it / 2 } +
                    fadeOut(LedgerMotion.short())
                enter togetherWith exit
            },
            label = "home-month",
        ) { month ->
            Text(
                DateTimeFormatters.yearMonth(month),
                modifier = Modifier.testTag("home-month"),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
        IconButton(onClick = onNextMonth, colors = iconColors) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "下一月",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MonthFiguresBlock(
    figures: MonthFigures,
    currencyCode: String,
    rule: Color,
) {
    val tones = LocalLedgerTones.current
    val balanceText = MoneyFormatter.format(figures.incomeMinor - figures.expenseMinor, currencyCode)
    val empty = figures.incomeMinor == 0L && figures.expenseMinor == 0L
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "本月结余",
            style = MaterialTheme.typography.labelLarge,
            color = tones.heroMuted,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedAmount(
            text = balanceText,
            yearMonth = figures.yearMonth,
            style = heroAmountStyle(balanceText),
            color = tones.accent,
            alignEnd = false,
            modifier = Modifier
                .testTag("home-balance")
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(rule),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewSecondary(
                label = "收入",
                amountMinor = figures.incomeMinor,
                currencyCode = currencyCode,
                type = TransactionType.INCOME,
                yearMonth = figures.yearMonth,
                alignEnd = false,
                testTag = "home-income",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .width(0.5.dp)
                    .height(32.dp)
                    .background(rule),
            )
            OverviewSecondary(
                label = "支出",
                amountMinor = figures.expenseMinor,
                currencyCode = currencyCode,
                type = TransactionType.EXPENSE,
                yearMonth = figures.yearMonth,
                alignEnd = true,
                testTag = "home-expense",
                modifier = Modifier.weight(1f),
            )
        }
        if (empty) {
            Spacer(Modifier.height(8.dp))
            Text(
                "本月还没有收支",
                modifier = Modifier
                    .testTag("home-empty")
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = tones.heroMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Immutable
private data class MonthFigures(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

@Composable
private fun AnimatedAmount(
    text: String,
    yearMonth: YearMonth,
    style: TextStyle,
    color: Color,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = yearMonth to text,
        modifier = modifier,
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        transitionSpec = {
            val forward = targetState.first >= initialState.first
            val enter = slideInVertically(LedgerMotion.medium()) { if (forward) it / 2 else -it / 2 } +
                fadeIn(LedgerMotion.short())
            val exit = slideOutVertically(LedgerMotion.medium()) { if (forward) -it / 2 else it / 2 } +
                fadeOut(LedgerMotion.short())
            enter togetherWith exit
        },
        label = "amount",
    ) { (_, value) ->
        Text(
            value,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}

@Composable
private fun heroAmountStyle(text: String): TextStyle {
    val typography = MaterialTheme.typography
    return when {
        text.length <= 10 -> typography.displayMedium
        text.length <= 12 -> typography.displaySmall
        text.length <= 16 -> typography.headlineMedium.asHeroNumber((-0.4).sp)
        else -> typography.headlineSmall.asHeroNumber((-0.3).sp)
    }
}

private fun TextStyle.asHeroNumber(tracking: TextUnit): TextStyle = copy(
    fontWeight = FontWeight.Light,
    letterSpacing = tracking,
    fontFeatureSettings = "tnum",
)

@Composable
private fun OverviewSecondary(
    label: String,
    amountMinor: Long,
    currencyCode: String,
    type: TransactionType,
    yearMonth: YearMonth,
    alignEnd: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val tones = LocalLedgerTones.current
    val alignment = if (alignEnd) Alignment.End else Alignment.Start
    val amount = MoneyFormatter.format(amountMinor, currencyCode)
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tones.heroMuted,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedAmount(
            text = amount,
            yearMonth = yearMonth,
            style = secondaryAmountStyle(amount),
            color = if (amountMinor == 0L) tones.heroMuted else amountColor(type),
            alignEnd = alignEnd,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun secondaryAmountStyle(text: String): TextStyle {
    val typography = MaterialTheme.typography
    val base = when {
        text.length <= 10 -> typography.titleLarge
        text.length <= 12 -> typography.titleMedium
        else -> typography.bodyMedium
    }
    return base.copy(fontFeatureSettings = "tnum", letterSpacing = (-0.15).sp)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp),
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
    val progress = remember(line.id) { Animatable(0f) }
    LaunchedEffect(line.id, fraction) {
        if (LedgerMotion.animationsEnabled()) {
            progress.animateTo(fraction, LedgerMotion.medium())
        } else {
            progress.snapTo(fraction)
        }
    }
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
                size = Size(size.width * progress.value, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Preview(name = "首页顶部", showBackground = true, widthDp = 360, backgroundColor = 0xFFF4EFE6)
@Preview(
    name = "首页顶部 · 深色",
    showBackground = true,
    widthDp = 360,
    backgroundColor = 0xFF101216,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeTopPreview() {
    LocalLedgerTheme {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(HomeListPadding),
        ) {
            HomeTopSection(
                ledgerName = "日常账本",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 1_280_000,
                expenseMinor = 43_650,
                currencyCode = "CNY",
                onLedgerClick = {},
                onPreviousMonth = {},
                onNextMonth = {},
            )
        }
    }
}

@Preview(name = "首页顶部 · 空", showBackground = true, widthDp = 360, backgroundColor = 0xFFF4EFE6)
@Composable
private fun HomeTopEmptyPreview() {
    LocalLedgerTheme {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(HomeListPadding),
        ) {
            HomeTopSection(
                ledgerName = "",
                yearMonth = YearMonth.of(2026, 10),
                incomeMinor = 0,
                expenseMinor = 0,
                currencyCode = "CNY",
                onLedgerClick = {},
                onPreviousMonth = {},
                onNextMonth = {},
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
