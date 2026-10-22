package com.example.localledger.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.money.RatioMath
import com.example.localledger.domain.report.PeriodReport
import com.example.localledger.domain.report.ReportKind
import com.example.localledger.domain.report.WeekSlice
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.LedgerHairline
import com.example.localledger.ui.component.MoneyText
import com.example.localledger.ui.home.SectionTitle
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.amountColor
import com.example.localledger.ui.theme.chartPalette
import androidx.compose.material3.Icon

@Composable
internal fun ReportCard(report: PeriodReport, modifier: Modifier = Modifier) {
    LedgerCard(modifier = modifier.fillMaxWidth().testTag("report-card")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
                .semantics { contentDescription = report.prose },
        ) {
            Text(
                "牛牛记账",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (report.kind == ReportKind.WEEK) "周报" else "月报",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(report.periodLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                "${report.ledgerName} · ${report.currencyLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    report.prose,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TotalColumn("收入", report.incomeMinor, amountColor(TransactionType.INCOME), report.currencyCode)
                TotalColumn("支出", report.expenseMinor, amountColor(TransactionType.EXPENSE), report.currencyCode)
                TotalColumn(
                    "结余",
                    report.balanceMinor,
                    if (report.balanceMinor < 0) amountColor(TransactionType.EXPENSE) else MaterialTheme.colorScheme.primary,
                    report.currencyCode,
                )
            }
            Spacer(Modifier.height(12.dp))
            ComparisonLine(report.incomeComparison)
            ComparisonLine(report.expenseComparison)
            ComparisonLine(report.balanceComparison)
            if (report.expenseCategories.isEmpty()) {
                if (report.expenseMinor == 0L) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "这段时间没有支出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionTitle("支出分类")
                CategoryRows(report.expenseCategories, report.currencyCode, usePalette = true)
            }
            if (report.incomeCategories.isNotEmpty()) {
                SectionTitle("收入分类")
                CategoryRows(
                    report.incomeCategories,
                    report.currencyCode,
                    usePalette = false,
                    barColor = amountColor(TransactionType.INCOME),
                )
            }
            if (report.days.isNotEmpty()) {
                SectionTitle(if (report.kind == ReportKind.WEEK) "每日趋势" else "每日支出")
                ReportTrend(report.days, weekly = report.kind == ReportKind.WEEK)
            }
            if (report.weeks.size > 1) {
                SectionTitle("分周")
                WeekRows(report.weeks, report.currencyCode)
            }
            if (report.highlights.isNotEmpty()) {
                SectionTitle("要点")
                report.highlights.forEachIndexed { index, line ->
                    if (index > 0) LedgerHairline()
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(line.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(line.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TotalColumn(label: String, amountMinor: Long, color: Color, currencyCode: String) {
    Column(Modifier.weight(1f).padding(end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        MoneyText(
            amountMinor = amountMinor,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            currencyCode = currencyCode,
        )
    }
}

@Composable
private fun ComparisonLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Immutable
private data class BarFraction(val fraction: Float)

@Composable
private fun CategoryRows(
    categories: List<CategoryTotal>,
    currencyCode: String,
    usePalette: Boolean,
    barColor: Color = amountColor(TransactionType.EXPENSE),
) {
    val total = remember(categories) {
        categories.fold(0L) { acc, item -> Math.addExact(acc, item.amountMinor) }
    }
    val fractions = remember(categories, total) {
        categories.map { BarFraction(RatioMath.fraction(it.amountMinor, total)) }
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val palette = chartPalette(LocalLedgerDarkTheme.current)
    Column {
        categories.forEachIndexed { index, category ->
            val color = if (usePalette) palette[index % palette.size] else barColor
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.resolve(category.iconKey).image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = color,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            RatioMath.percentLabel(category.amountMinor, total),
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
                    val fraction = fractions[index].fraction
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(color = track, cornerRadius = radius)
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = color,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTrend(days: List<DailyTotal>, weekly: Boolean) {
    val fractions = remember(days) {
        val incomeMax = days.maxOf { it.incomeMinor }.coerceAtLeast(1L)
        val expenseMax = days.maxOf { it.expenseMinor }.coerceAtLeast(1L)
        days.map {
            RatioMath.fraction(it.incomeMinor, incomeMax) to RatioMath.fraction(it.expenseMinor, expenseMax)
        }
    }
    val income = amountColor(TransactionType.INCOME)
    val expense = amountColor(TransactionType.EXPENSE)
    val baseline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val quiet = fractions.all { (incomeFraction, expenseFraction) ->
        incomeFraction <= 0f && expenseFraction <= 0f
    }
    Column {
        if (!quiet) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (weekly) TrendLegend("收入", income)
                TrendLegend("支出", expense)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (quiet) {
            Text(
                "这几天没有收支",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (weekly) 96.dp else 112.dp)
                .semantics {
                    contentDescription = if (weekly) {
                        "每日趋势，收入和支出各自按最高一天绘制，共${days.size}天"
                    } else {
                        "每日支出，共${days.size}天"
                    }
                },
        ) {
            drawLine(
                color = baseline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            if (fractions.isEmpty()) return@Canvas
            val slot = size.width / fractions.size
            val gap = 2.dp.toPx()
            val pair = if (weekly) 2 else 1
            val bar = (slot * if (weekly) 0.28f else 0.55f / pair).coerceIn(1.5f, 14.dp.toPx())
            val radius = CornerRadius(minOf(bar / 3f, 3.dp.toPx()), minOf(bar / 3f, 3.dp.toPx()))
            val minBar = 3.dp.toPx()
            fractions.forEachIndexed { index, (incomeFraction, expenseFraction) ->
                val group = bar * pair + if (weekly) gap else 0f
                var left = slot * index + (slot - group) / 2f
                if (weekly && incomeFraction > 0f) {
                    val height = maxOf(size.height * incomeFraction, minBar)
                    drawRoundRect(
                        color = income,
                        topLeft = Offset(left, size.height - height),
                        size = Size(bar, height),
                        cornerRadius = radius,
                    )
                }
                if (weekly) left += bar + gap
                if (expenseFraction > 0f) {
                    val height = maxOf(size.height * expenseFraction, minBar)
                    drawRoundRect(
                        color = expense,
                        topLeft = Offset(left, size.height - height),
                        size = Size(bar, height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        if (weekly && days.size == 7) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekRows(weeks: List<WeekSlice>, currencyCode: String) {
    val fractions = remember(weeks) {
        val max = weeks.maxOf { it.expenseMinor }.coerceAtLeast(1L)
        weeks.map { RatioMath.fraction(it.expenseMinor, max) }
    }
    val expense = amountColor(TransactionType.EXPENSE)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        weeks.forEachIndexed { index, slice ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(108.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val fraction = fractions[index]
                Canvas(Modifier.weight(1f).height(6.dp)) {
                    val radius = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(color = track, cornerRadius = radius)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = expense,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                MoneyText(
                    amountMinor = slice.expenseMinor,
                    style = MaterialTheme.typography.bodySmall,
                    currencyCode = currencyCode,
                )
            }
        }
    }
}
