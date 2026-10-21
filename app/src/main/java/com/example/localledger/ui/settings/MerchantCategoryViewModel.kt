package com.example.localledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.data.repository.MerchantCategoryBook
import com.example.localledger.data.repository.MerchantCategoryRule
import com.example.localledger.ui.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MerchantCategoryUiState(
    val rules: List<MerchantCategoryRule> = emptyList(),
    val ready: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantCategoryViewModel @Inject constructor(
    private val appSession: AppSession,
    private val book: MerchantCategoryBook,
) : ViewModel() {
    val uiState: StateFlow<MerchantCategoryUiState> = appSession.state
        .map { it.currentLedgerId }
        .flatMapLatest { ledgerId ->
            if (ledgerId == null) {
                flowOf(MerchantCategoryUiState(ready = true))
            } else {
                book.observe(ledgerId).map { MerchantCategoryUiState(it, ready = true) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MerchantCategoryUiState())

    fun clear(id: String) {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        viewModelScope.launch { book.clear(ledgerId, id) }
    }

    fun clearAll() {
        val ledgerId = appSession.state.value.currentLedgerId ?: return
        viewModelScope.launch { book.clearAll(ledgerId) }
    }
}
