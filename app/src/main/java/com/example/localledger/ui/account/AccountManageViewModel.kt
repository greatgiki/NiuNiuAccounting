package com.example.localledger.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localledger.core.money.MoneyParser
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountBalance
import com.example.localledger.domain.model.AccountType
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.repository.AccountRepository
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.common.UiOutcome
import com.example.localledger.ui.common.runOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountManageUiState(
    val isLoading: Boolean = true,
    val balances: List<AccountBalance> = emptyList(),
    val error: String? = null,
)

sealed interface AccountEffect {
    data class ShowMessage(val message: String) : AccountEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountManageViewModel @Inject constructor(
    appSession: AppSession,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val effectChannel = Channel<AccountEffect>(Channel.BUFFERED)
    private val refreshFlow = MutableStateFlow(0)
    val effects = effectChannel.receiveAsFlow()

    val uiState: StateFlow<AccountManageUiState> = combine(
        appSession.state.map { it.currentLedgerId }, refreshFlow,
    ) { ledgerId, _ -> ledgerId }
        .flatMapLatest { ledgerId ->
            if (ledgerId == null) {
                flowOf(AccountManageUiState(isLoading = false))
            } else {
                accountRepository.observeAccountBalances(ledgerId)
                    .map { AccountManageUiState(isLoading = false, balances = it) }
            }
        }
        .catch { error ->
            emit(AccountManageUiState(isLoading = false, error = error.message ?: "读取账户失败"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountManageUiState())

    suspend fun save(
        existing: Account?,
        ledgerId: String,
        name: String,
        type: AccountType,
        initialBalanceText: String,
        sortOrderText: String,
        currencyCode: String,
    ): UiOutcome {
        val initialBalance = try {
            val digits = CurrencyCatalog.of(currencyCode).minorDigits
            MoneyParser.toSignedMinor(initialBalanceText.ifBlank { "0" }, digits)
        } catch (error: LedgerException) {
            return UiOutcome.Failure("初始余额格式不正确")
        }
        val now = System.currentTimeMillis()
        val account = Account(
            id = existing?.id ?: UUID.randomUUID().toString(),
            ledgerId = ledgerId,
            name = name.trim(),
            type = type,
            currencyCode = currencyCode,
            initialBalanceMinor = initialBalance,
            sortOrder = sortOrderText.toIntOrNull() ?: 0,
            isArchived = existing?.isArchived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return runOutcome("保存账户失败") { accountRepository.saveAccount(account) }
    }

    suspend fun archive(accountId: String): UiOutcome =
        runOutcome("停用账户失败") { accountRepository.archiveAccount(accountId) }

    fun retry() { refreshFlow.value += 1 }
}
