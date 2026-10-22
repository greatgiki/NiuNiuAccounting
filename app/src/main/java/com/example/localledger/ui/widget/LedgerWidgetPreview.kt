package com.example.localledger.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.amountColorFor
import com.example.localledger.widget.WidgetAmounts
import com.example.localledger.widget.WidgetSnapshot
import com.example.localledger.domain.model.TransactionType

/**
 * Compose stand-in for the Glance home widget, used by previews and screenshots.
 * The installed widget is [com.example.localledger.widget.LedgerSummaryWidget].
 */
@Composable
fun LedgerWidgetPreview(
    snapshot: WidgetSnapshot,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val dark = LocalLedgerDarkTheme.current
    Surface(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "牛牛记账",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "记一笔",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            PreviewPeriod("今日", snapshot.today, snapshot, dark)
            Spacer(Modifier.height(8.dp))
            PreviewPeriod("本月", snapshot.month, snapshot, dark)
        }
    }
}

@Composable
private fun PreviewPeriod(
    label: String,
    figures: IncomeExpenseSummary,
    snapshot: WidgetSnapshot,
    dark: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f),
        )
        PreviewFigure(
            "收入",
            WidgetAmounts.text(figures.incomeMinor, snapshot.currencyCode, snapshot.maskAmounts),
            amountColorFor(dark, TransactionType.INCOME),
            Modifier.weight(1f),
        )
        PreviewFigure(
            "支出",
            WidgetAmounts.text(figures.expenseMinor, snapshot.currencyCode, snapshot.maskAmounts),
            amountColorFor(dark, TransactionType.EXPENSE),
            Modifier.weight(1f),
        )
        PreviewFigure(
            "结余",
            WidgetAmounts.text(figures.balanceMinor, snapshot.currencyCode, snapshot.maskAmounts),
            if (figures.balanceMinor < 0) {
                amountColorFor(dark, TransactionType.EXPENSE)
            } else {
                MaterialTheme.colorScheme.primary
            },
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviewFigure(
    label: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            amount,
            color = color,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}
