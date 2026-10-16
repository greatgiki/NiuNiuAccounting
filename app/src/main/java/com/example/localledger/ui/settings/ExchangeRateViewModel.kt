package com.example.localledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.money.CfetsCcpr
import com.example.localledger.domain.repository.ExchangeRateRepository
import com.example.localledger.domain.usecase.RefreshExchangeRateResult
import com.example.localledger.domain.usecase.RefreshExchangeRatesUseCase
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.DateTimeFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    appSession: AppSession,
    repository: ExchangeRateRepository,
    private val refreshRates: RefreshExchangeRatesUseCase,
) : ViewModel() {
    private val beijing = CfetsCcpr.BEIJING
    private val messages = Channel<String>(Channel.BUFFERED)
    val effects = messages.receiveAsFlow()

    val uiState: StateFlow<ExchangeRateUiState> = appSession.state
        .flatMapLatest { session ->
            repository.observe(session.currentLedgerCurrency).map { snapshot ->
                snapshot.toState(session.currentLedgerCurrency)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExchangeRateUiState())

    fun refresh() {
        val base = uiState.value.currencyCode
        viewModelScope.launch {
            when (val result = refreshRates(base)) {
                is RefreshExchangeRateResult.Updated ->
                    messages.send("汇率已更新")
                is RefreshExchangeRateResult.KeptCached ->
                    messages.send("刷新失败，继续使用 ${result.snapshot.updatedLabel()}")
                is RefreshExchangeRateResult.Failed ->
                    messages.send(result.message)
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
