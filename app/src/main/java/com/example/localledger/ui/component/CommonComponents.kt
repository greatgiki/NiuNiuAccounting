package com.example.localledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.MoneyFormatter
import com.example.localledger.ui.common.categoryAccent
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
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
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
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    CategoryIcons.resolve(iconKey).image
                },
                contentDescription = title,
                modifier = Modifier.size(22.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            TransactionAmountText(type = type, amountMinor = amountMinor, currencyCode = currencyCode)
            Text(
                DateTimeFormatters.time(timeMillis, zoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FormRowHeight(): Dp = 56.dp
