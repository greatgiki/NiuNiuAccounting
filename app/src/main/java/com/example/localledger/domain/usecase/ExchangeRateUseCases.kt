package com.example.localledger.domain.usecase

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.repository.ExchangeRateRemote
import com.example.localledger.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface RefreshExchangeRateResult {
    data class Updated(val snapshot: ExchangeRateSnapshot) : RefreshExchangeRateResult
    data class KeptCached(val snapshot: ExchangeRateSnapshot, val message: String) : RefreshExchangeRateResult
    data class Failed(val message: String) : RefreshExchangeRateResult
}

class RefreshExchangeRatesUseCase @Inject constructor(
    private val remote: ExchangeRateRemote,
    private val repository: ExchangeRateRepository,
) {
    suspend operator fun invoke(baseCode: String): RefreshExchangeRateResult {
        return try {
            val snapshot = remote.fetch(baseCode)
            repository.save(snapshot)
            RefreshExchangeRateResult.Updated(snapshot)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val cached = repository.get(baseCode)
            val message = (error as? LedgerException)?.message ?: "汇率刷新失败"
            if (cached != null) {
                RefreshExchangeRateResult.KeptCached(cached, message)
            } else {
                RefreshExchangeRateResult.Failed(message)
            }
        }
    }

    suspend fun refreshAll(): List<RefreshExchangeRateResult> {
        val bases = repository.baseCodesInUse().ifEmpty { listOf("CNY") }.distinct()
        return bases.map { invoke(it) }
    }
}
