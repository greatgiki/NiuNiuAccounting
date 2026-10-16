package com.example.localledger.domain.importing

object ImportNote {
    fun compose(counterparty: String, goods: String, remark: String): String? {
        return listOf(counterparty, goods, remark)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "/" }
            .distinct()
            .joinToString(" · ")
            .take(200)
            .ifBlank { null }
    }
}
