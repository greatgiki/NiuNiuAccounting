package com.example.localledger.domain.importing

import com.example.localledger.domain.model.Category
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType

/**
 * Merchant memory wins over keyword rules. An archived or missing mapping
 * falls through to the keyword table, then to 「其他」.
 */
object CategoryGuess {
    fun idFor(
        type: TransactionType,
        text: String,
        categories: List<Category>,
        mappedCategoryId: String? = null,
    ): String? {
        if (type == TransactionType.TRANSFER) return null
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val active = categories.filter { !it.isArchived && it.type == categoryType }
        if (!mappedCategoryId.isNullOrBlank()) {
            active.firstOrNull { it.id == mappedCategoryId }?.let { return it.id }
        }
        val name = CategoryKeywordMapper.suggestName(type, text)
        return active.firstOrNull { it.name == name }?.id
            ?: active.firstOrNull { it.name == "其他" }?.id
    }
}
