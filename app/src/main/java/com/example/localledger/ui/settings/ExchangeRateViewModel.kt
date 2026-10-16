package com.example.localledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.money.CfetsCcpr
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.repository.LedgerRepository
import kotlinx.coroutines.CancellationException
import com.example.localledger.domain.usecase.RefreshExchangeRateResult
import com.example.localledger.domain.usecase.RefreshExchangeRatesUseCase
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import com.example.localledger.ui.common.UiOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangeRateUiState(
    val currencyCode: String = "CNY",
    val updatedText: String = "尚未获取汇率",
    val source: String = "",
    val refreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExchangeRateViewModel @Inject constructor(
    private val appSession: AppSession,
    repository: ExchangeRateRepository,
    private val refreshRates: RefreshExchangeRatesUseCase,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val beijing = CfetsCcpr.BEIJING
    private val messages = Channel<String>(Channel.BUFFERED)
    val effects = messages.receiveAsFlow()
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<ExchangeRateUiState> = combine(
        appSession.state.flatMapLatest { session ->
            repository.observe(session.currentLedgerCurrency).map { snapshot ->
                snapshot.toState(session.currentLedgerCurrency)
            }
        },
        refreshing,
    ) { state, busy -> state.copy(refreshing = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExchangeRateUiState())

    suspend fun revalueWithCurrentRates(): UiOutcome {
        val ledgerId = appSession.state.value.currentLedgerId
            ?: return UiOutcome.Failure("请先选择账本")
        return try {
            val count = ledgerRepository.revalueWithCurrentRates(ledgerId)
            messages.send(if (count == 0) "没有需要重算的流水" else "已按当前汇率重算 $count 笔流水的本位币金额")
            UiOutcome.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: LedgerException) {
            UiOutcome.Failure(error.message ?: "重算失败")
        }
    }

    fun refresh() {
        if (!refreshing.compareAndSet(false, true)) return
        val base = uiState.value.currencyCode
        viewModelScope.launch {
            try {
                when (val result = refreshRates(base)) {
                    is RefreshExchangeRateResult.Updated ->
                        messages.send("汇率已更新")
                    is RefreshExchangeRateResult.KeptCached ->
                        messages.send("刷新失败，继续使用 ${result.snapshot.updatedLabel()}")
                    is RefreshExchangeRateResult.Failed ->
                        messages.send(result.message)
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun ExchangeRateSnapshot?.toState(currency: String): ExchangeRateUiState {
        if (this == null) return ExchangeRateUiState(currencyCode = currency)
        return ExchangeRateUiState(
            currencyCode = currency,
            updatedText = "发布时间 ${DateTimeFormatters.dateTime(fetchedAt, beijing)}（北京时间）",
            source = source,
        )
    }

    private fun ExchangeRateSnapshot.updatedLabel(): String =
        "${DateTimeFormatters.dateTime(fetchedAt, beijing)}（北京时间）"
}
