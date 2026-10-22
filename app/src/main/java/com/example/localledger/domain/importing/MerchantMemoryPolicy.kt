package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType

/**
 * Remember a merchant only when the user actually changes the category.
 * Accepting a guess, or saving again without a new category, does not overwrite.
 */
object MerchantMemoryPolicy {
    fun shouldRemember(
        type: TransactionType,
        merchant: String?,
        categoryId: String?,
        previousCategoryId: String?,
        categoryChanged: Boolean,
    ): Boolean {
        if (!categoryChanged) return false
        if (type == TransactionType.TRANSFER) return false
        if (categoryId.isNullOrBlank()) return false
        if (categoryId == previousCategoryId) return false
        return MerchantKey.normalize(merchant.orEmpty()).isNotEmpty()
    }
}
