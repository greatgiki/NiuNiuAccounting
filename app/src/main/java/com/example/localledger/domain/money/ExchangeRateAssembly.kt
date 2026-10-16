package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

/**
 * 汇率来源顺序：
 * 1. 中国外汇交易中心人民币中间价（人民币标价）
 * 2. 仍缺的币种用 [supplement] 补齐（面向大陆可访问的公开表）
 * 3. 还缺则用 [gapFillers]（同样是 1 人民币兑外币）
 * 4. 中间价完全不可用，或补齐后仍没有本位币时，改用 [direct] 里已经按本位币报价的来源，
 *    前面的表优先，后面的表只补缺
 */
class ExchangeRateAssembly(
    private val cfets: () -> CnyQuotedRates,
    private val supplement: () -> CnyQuotedRates,
    private val gapFillers: List<() -> CnyQuotedRates> = emptyList(),
    private val direct: List<() -> ExchangeRateSnapshot> = emptyList(),
) {
    fun fetch(baseCode: String): ExchangeRateSnapshot {
        val base = CurrencyCatalog.of(baseCode).code
        val board = LinkedHashMap<String, BigDecimal>()
        val sources = mutableListOf<String>()
        var publishedAt: Long? = null

        fun missing(): Set<String> = CurrencyCatalog.supported
            .map { it.code }
            .filter { it != "CNY" && it !in board }
            .toSet()

        fun take(rates: CnyQuotedRates?) {
            if (rates == null || rates.foreignPerOneCny.isEmpty()) return
            val filled = absorb(board, rates.foreignPerOneCny, missing())
            if (filled.isEmpty()) return
            sources += if (sources.isEmpty()) rates.source else "${rates.source} 补齐未公布币种"
            if (publishedAt == null) publishedAt = rates.publishedAtMillis
        }

        take(attempt(cfets))
        if (missing().isNotEmpty()) take(attempt(supplement))
        for (filler in gapFillers) {
            if (missing().isEmpty()) break
            take(attempt(filler))
        }

        val canCross = board.isNotEmpty() && (base == "CNY" || base in board)
        if (canCross) {
            return ExchangeRateSnapshot(
                baseCode = base,
                quotesPerUnit = CnyCrossRates.quotesPerUnit(board, base),
                fetchedAt = publishedAt ?: throw LedgerException.ExchangeRateUnavailable(),
                source = sources.joinToString("；"),
            )
        }

        val snapshots = direct.mapNotNull { attempt(it) }.filter { it.baseCode == base && it.quotesPerUnit.isNotEmpty() }
        return mergeDirect(base, snapshots) ?: throw LedgerException.ExchangeRateUnavailable()
    }

    private fun absorb(
        board: MutableMap<String, BigDecimal>,
        foreignPerOneCny: Map<String, BigDecimal>,
        only: Set<String>,
    ): Set<String> {
        val filled = linkedSetOf<String>()
        only.forEach { code ->
            val quote = foreignPerOneCny[code] ?: return@forEach
            if (quote.signum() <= 0) return@forEach
            board[code] = quote
            filled += code
        }
        return filled
    }

    private fun mergeDirect(base: String, snapshots: List<ExchangeRateSnapshot>): ExchangeRateSnapshot? {
        if (snapshots.isEmpty()) return null
        val quotes = LinkedHashMap<String, BigDecimal>()
        val sources = mutableListOf<String>()
        var publishedAt = snapshots.first().fetchedAt
        snapshots.forEach { snapshot ->
            var added = false
            snapshot.quotesPerUnit.forEach { (code, quote) ->
                if (code == base || code in quotes || quote.signum() <= 0 || !CurrencyCatalog.contains(code)) return@forEach
                quotes[code] = quote
                added = true
            }
            if (added) {
                sources += if (sources.isEmpty()) snapshot.source else "${snapshot.source} 补齐未公布币种"
                if (sources.size == 1) publishedAt = snapshot.fetchedAt
            }
        }
        if (quotes.isEmpty()) return null
        return ExchangeRateSnapshot(base, quotes, publishedAt, sources.joinToString("；"))
    }

    private inline fun <T> attempt(block: () -> T): T? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}
