package com.example.localledger.widget

import android.content.Context
import android.util.Log
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.domain.widget.WidgetPrivacy
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

internal object WidgetSnapshotLoader {
    suspend fun load(context: Context): WidgetSnapshot {
        val entry = widgetEntryPoint(context)
        val lockOn = entry.appLockStore().isConfigured()
        val settings = entry.settingsStore().peek()
        val mask = WidgetPrivacy.maskAmounts(lockOn, settings?.widgetMaskAmountsWhenLocked ?: true)
        val ledgerId = settings?.currentLedgerId ?: return WidgetSnapshot.empty(mask)
        return try {
            val database = entry.databaseProvider().get()
            val ledger = database.ledgerDao().findById(ledgerId) ?: return WidgetSnapshot.empty(mask)
            val zone = entry.zoneIdProvider().current()
            val resolver = DateRangeResolver(entry.zoneIdProvider())
            val today = LocalDate.now(zone)
            val day = resolver.resolve(StatisticsPeriod.Day(today))
            val month = resolver.resolve(StatisticsPeriod.Month(java.time.YearMonth.from(today)))
            val dao = database.transactionDao()
            val todayRow = dao.summary(ledgerId, day.startInclusive, day.endExclusive)
            val monthRow = dao.summary(ledgerId, month.startInclusive, month.endExclusive)
            WidgetSnapshot(
                currencyCode = ledger.currencyCode,
                today = IncomeExpenseSummary(todayRow.incomeMinor, todayRow.expenseMinor),
                month = IncomeExpenseSummary(monthRow.incomeMinor, monthRow.expenseMinor),
                maskAmounts = mask,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "读取小组件数据失败", error)
            WidgetSnapshot.empty(mask)
        }
    }

    private const val TAG = "NiuNiuWidget"
}
