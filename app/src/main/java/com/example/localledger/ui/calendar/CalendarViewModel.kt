package com.example.localledger.ui.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.local.database.WalletDatabaseSource
import com.example.localledger.domain.calendar.CalendarGrid
import com.example.localledger.domain.calendar.DatedAmount
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.StatisticsPeriod
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionFilter
import com.example.localledger.domain.repository.TransactionRepository
import com.example.localledger.domain.time.DateRangeResolver
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private data class DaySlice(
    val date: LocalDate,
    val rows: List<TransactionDetail>,
    val ready: Boolean,
)

@Immutable
data class CalendarUiState(
    val ledgerId: String? = null,
    val currencyCode: String = "CNY",
    val today: LocalDate,
    val selected: LocalDate,
    val months: Map<YearMonth, Map<LocalDate, IncomeExpenseSummary>> = emptyMap(),
    val dayRows: List<TransactionDetail> = emptyList(),
    val dayReady: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val appSession: AppSession,
    private val databaseProvider: WalletDatabaseSource,
    private val transactionRepository: TransactionRepository,
    private val dateRangeResolver: DateRangeResolver,
) : ViewModel() {
    private val zone = dateRangeResolver.zoneId()
    private val today = LocalDate.now(zone)
    private val selected = MutableStateFlow(today)
    private val months = MutableStateFlow<Map<YearMonth, Map<LocalDate, IncomeExpenseSummary>>>(emptyMap())
    private val loaded = ConcurrentHashMap.newKeySet<String>()
    private val remembered = HashMap<YearMonth, LocalDate>()

    @Volatile
    private var cacheLedger: String? = null

    private val dayRows = combine(
        appSession.state.map { it.currentLedgerId }.distinctUntilChanged(),
        selected,
    ) { ledgerId, date -> ledgerId to date }
        .flatMapLatest { (ledgerId, date) ->
            if (ledgerId == null) {
                kotlinx.coroutines.flow.flowOf(DaySlice(date, emptyList(), ready = true))
            } else {
                val range = dateRangeResolver.resolve(StatisticsPeriod.Day(date))
                transactionRepository.observeTransactions(
                    ledgerId,
                    TransactionFilter(range = range),
                ).map { rows -> DaySlice(date, rows, ready = true) }
                    .onStart { emit(DaySlice(date, emptyList(), ready = false)) }
                    .flowOn(Dispatchers.IO)
            }
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        appSession.state,
        selected,
        months,
        dayRows,
    ) { session, date, monthMap, day ->
        CalendarUiState(
            ledgerId = session.currentLedgerId,
            currencyCode = session.currentLedgerCurrency,
            today = today,
            selected = date,
            months = monthMap,
            dayRows = if (day.date == date) day.rows else emptyList(),
            dayReady = day.date == date && day.ready,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(today = today, selected = today),
    )

    fun select(date: LocalDate) {
        remembered[YearMonth.from(date)] = date
        selected.value = date
    }

    fun onVisibleMonth(month: YearMonth) {
        val current = selected.value
        if (YearMonth.from(current) == month) return
        val next = remembered[month]
            ?: if (month == YearMonth.from(today)) today else month.atDay(1)
        selected.value = next
    }

    fun ensureMonth(month: YearMonth) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        synchronized(loaded) {
            if (cacheLedger != ledgerId) {
                cacheLedger = ledgerId
                loaded.clear()
                months.value = emptyMap()
            }
            if (!loaded.add("$ledgerId|$month")) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val range = dateRangeResolver.resolve(StatisticsPeriod.Month(month))
                val rows = databaseProvider.get().transactionDao().listDayAmounts(
                    ledgerId,
                    range.startInclusive,
                    range.endExclusive,
                )
                if (cacheLedger != ledgerId) return@launch
                val bucket = CalendarGrid.bucket(
                    rows.map { DatedAmount(it.transactionAt, it.type, it.baseAmountMinor) },
                    zone,
                )
                months.update { current ->
                    if (cacheLedger != ledgerId) current else current + (month to bucket)
                }
            } catch (error: CancellationException) {
                loaded.remove("$ledgerId|$month")
                throw error
            } catch (_: Exception) {
                loaded.remove("$ledgerId|$month")
            }
        }
    }
}
