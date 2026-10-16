package com.example.localledger.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.RatioMath
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.ErrorState
import com.example.localledger.ui.component.LoadingState
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.component.SummaryAmountRow
import com.example.localledger.ui.component.TransactionRowContent
import com.example.localledger.ui.navigation.Routes
import com.example.localledger.ui.navigation.navigateToTransactions
import com.example.localledger.ui.theme.amountColor
import kotlinx.coroutines.launch

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
                    Text(
                        DateTimeFormatters.yearMonth(state.yearMonth),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一月",
                        )
                    }
                }
            }
            item(key = "summary") {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    SummaryAmountRow(
                        incomeMinor = state.incomeMinor,
                        expenseMinor = state.expenseMinor,
                        modifier = Modifier.padding(16.dp),
                        currencyCode = state.currencyCode,
                    )
                }
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
                items(state.budgets, key = { "budget-${it.id}" }, contentType = { "budget" }) { line ->
                    BudgetProgressRow(line, state.currencyCode)
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
                items(
                    state.accountBalances,
                    key = { "balance-${it.account.id}" },
                    contentType = { "balance" },
                ) { balance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { navController.navigate(Routes.ACCOUNTS) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = balance.account.name,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    HorizontalDivider()
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
                        message = "还没有流水，点击右下角记一笔",
                        actionLabel = "记一笔",
                        onAction = { navController.navigate(Routes.transactionNew()) },
                    )
                }
            } else {
                items(
                    state.recentTransactions,
                    key = { "recent-${it.transaction.id}" },
                    contentType = { "recent" },
                ) { detail ->
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
                    HorizontalDivider()
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 12.dp),
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
fun BudgetProgressRow(line: HomeBudgetLine, currencyCode: String) {
    val fraction = RatioMath.fraction(line.spentMinor.coerceAtLeast(0), line.limitMinor).coerceIn(0f, 1f)
    val barColor = amountColor(if (line.overspent) TransactionType.EXPENSE else TransactionType.INCOME)
    val status = if (line.overspent) {
        "超支 ${MoneyFormatter.format(-line.remainingMinor, currencyCode)}"
    } else {
        "剩余 ${MoneyFormatter.format(line.remainingMinor, currencyCode)} / ${MoneyFormatter.format(line.limitMinor, currencyCode)}"
    }
    val description = "${line.label}，$status，已用 ${line.percentLabel}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (line.overspent) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
            drawRect(color = barColor.copy(alpha = 0.25f), size = size)
            drawRect(color = barColor, size = Size(size.width * fraction, size.height))
        }
    }
}
