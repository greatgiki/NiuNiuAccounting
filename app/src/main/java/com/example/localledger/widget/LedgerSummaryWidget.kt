package com.example.localledger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.color.ColorProvider
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import com.example.localledger.MainActivity
import com.example.localledger.domain.model.IncomeExpenseSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LedgerSummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { WidgetSnapshotLoader.load(context) }
        provideContent {
            LedgerWidgetBody(snapshot)
        }
    }
}

class LedgerSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LedgerSummaryWidget()
}

private val Muted = dayNight(Color(0xFF5C564C), Color(0xFFC4BBAE))
private val Gold = dayNight(Color(0xFF6E5128), Color(0xFFD4B483))
private val Surface = dayNight(Color(0xFFF7F2EA), Color(0xFF1C2026))
private val Income = dayNight(Color(0xFF2F6B52), Color(0xFF8FBFAB))
private val Expense = dayNight(Color(0xFF8E3D32), Color(0xFFE0A598))
private val OnGold = dayNight(Color(0xFFFFF9F2), Color(0xFF2A2114))

private fun dayNight(day: Color, night: Color): GlanceColor = ColorProvider(day, night)

@Composable
private fun LedgerWidgetBody(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = "牛牛记账",
                style = TextStyle(color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "记一笔",
                style = TextStyle(color = OnGold, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .background(Gold)
                    .cornerRadius(14.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionStartActivity(addIntent(context))),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        PeriodRow("今日", snapshot.today, snapshot)
        Spacer(GlanceModifier.height(4.dp))
        PeriodRow("本月", snapshot.month, snapshot)
    }
}

@Composable
private fun PeriodRow(label: String, figures: IncomeExpenseSummary, snapshot: WidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = Muted, fontSize = 12.sp),
            modifier = GlanceModifier.width(36.dp),
        )
        Figure("收入", WidgetAmounts.text(figures.incomeMinor, snapshot.currencyCode, snapshot.maskAmounts), Income, GlanceModifier.defaultWeight())
        Figure("支出", WidgetAmounts.text(figures.expenseMinor, snapshot.currencyCode, snapshot.maskAmounts), Expense, GlanceModifier.defaultWeight())
        Figure(
            "结余",
            WidgetAmounts.text(figures.balanceMinor, snapshot.currencyCode, snapshot.maskAmounts),
            if (figures.balanceMinor < 0) Expense else Gold,
            GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun Figure(label: String, amount: String, color: GlanceColor, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(text = label, style = TextStyle(color = Muted, fontSize = 11.sp))
        Text(text = amount, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

private fun addIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_NEW_TRANSACTION, true)
    }
}
