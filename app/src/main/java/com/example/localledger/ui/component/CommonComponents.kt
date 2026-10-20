package com.example.localledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.navigation.claimPagerHorizontalGesture
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.common.categoryAccent
import com.example.localledger.ui.theme.LocalLedgerTones
import com.example.localledger.ui.theme.amountColor
import java.time.ZoneId

@Composable
fun MoneyText(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    signed: Boolean? = null,
    currencyCode: String = "CNY",
) {
    val text = when (signed) {
        true -> MoneyFormatter.formatSigned(amountMinor, positive = true, currencyCode = currencyCode)
        false -> MoneyFormatter.formatSigned(amountMinor, positive = false, currencyCode = currencyCode)
        null -> MoneyFormatter.format(amountMinor, currencyCode)
    }
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = text },
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
    )
}

@Composable
fun TransactionAmountText(
    type: TransactionType,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    currencyCode: String = "CNY",
) {
    val signed = when (type) {
        TransactionType.EXPENSE -> false
        TransactionType.INCOME -> true
        TransactionType.TRANSFER -> null
    }
    MoneyText(
        amountMinor = amountMinor,
        modifier = modifier,
        style = style,
        color = amountColor(type),
        signed = signed,
        currencyCode = currencyCode,
    )
}

@Composable
fun LoadingState(message: String = "加载中…") {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val tones = LocalLedgerTones.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(tones.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                tint = tones.accent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "删除",
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        icon = { Icon(confirmIcon(confirmLabel), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            val haptic = rememberLedgerHaptic()
            TextButton(
                onClick = {
                    if (!submitting) {
                        haptic()
                        onConfirm()
                    }
                },
                enabled = !submitting,
            ) {
                Text(
                    if (submitting) "处理中…" else confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        },
    )
}

private fun confirmIcon(confirmLabel: String): ImageVector = when (confirmLabel) {
    "归档" -> Icons.Outlined.Archive
    "停用" -> Icons.Outlined.VisibilityOff
    else -> Icons.Outlined.Delete
}

@Composable
fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = if (expanded) "收起$title" else "展开$title"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = action,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDeleteRequest()
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .claimPagerHorizontalGesture()
            .semantics { contentDescription = "向左滑动可删除" },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        },
    )
}

@Composable
fun SummaryAmountRow(
    incomeMinor: Long,
    expenseMinor: Long,
    modifier: Modifier = Modifier,
    currencyCode: String = "CNY",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryItem("收入", incomeMinor, TransactionType.INCOME, currencyCode)
        SummaryItem("支出", expenseMinor, TransactionType.EXPENSE, currencyCode)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "结余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                amountMinor = incomeMinor - expenseMinor,
                style = MaterialTheme.typography.titleMedium,
                currencyCode = currencyCode,
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, amountMinor: Long, type: TransactionType, currencyCode: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TransactionAmountText(
            type = type,
            amountMinor = amountMinor,
            style = MaterialTheme.typography.titleMedium,
            currencyCode = currencyCode,
        )
    }
}

@Composable
fun TransactionRowContent(
    title: String,
    subtitle: String,
    amountMinor: Long,
    type: TransactionType,
    timeMillis: Long,
    zoneId: ZoneId,
    iconKey: String?,
    modifier: Modifier = Modifier,
    currencyCode: String = "CNY",
) {
    val accent = categoryAccent(iconKey, type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (type == TransactionType.TRANSFER) {
                    Icons.AutoMirrored.Outlined.ArrowForward
                } else {
                    CategoryIcons.resolve(iconKey).image
                },
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(0.8f),
            horizontalAlignment = Alignment.End,
        ) {
            TransactionAmountText(type = type, amountMinor = amountMinor, currencyCode = currencyCode)
            Text(
                DateTimeFormatters.time(timeMillis, zoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
