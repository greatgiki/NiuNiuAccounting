package com.example.localledger.domain.money

import com.example.localledger.domain.model.ExchangeRateSnapshot

enum class RevalueMode {
    /**
     * 选用发布时间不晚于流水时间的最近一条缓存。
     * 如果这笔流水早于全部缓存，则改用当前（最新）一条。
     */
    AS_OF_TRANSACTION,

    /** 全部流水都用当前（最新）缓存，忽略流水日期。 */
    CURRENT,
}

object HistoricalRatePolicy {
    fun select(
        history: List<ExchangeRateSnapshot>,
        transactionAt: Long,
        mode: RevalueMode,
    ): ExchangeRateSnapshot? {
        if (history.isEmpty()) return null
        val ordered = history.sortedBy { it.fetchedAt }
        val current = ordered.last()
        if (mode == RevalueMode.CURRENT) return current
        return ordered.lastOrNull { it.fetchedAt <= transactionAt } ?: current
    }
}
