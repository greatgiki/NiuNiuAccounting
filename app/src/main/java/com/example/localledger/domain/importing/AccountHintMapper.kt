package com.example.localledger.domain.importing

import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.AccountType

data class AccountSuggestion(
    val accountId: String?,
    val matchedPaymentMethod: Boolean,
)

object AccountHintMapper {
    fun suggest(
        paymentMethod: String,
        source: BillSource,
        accounts: List<Account>,
        defaultAccountId: String?,
    ): AccountSuggestion {
        val active = accounts.filter { !it.isArchived }
        if (active.isEmpty()) return AccountSuggestion(null, false)
        val method = paymentMethod.trim()
        val matched = when {
            method.contains("花呗") -> named(active) { it.contains("花呗") } ?: named(active) { it.contains("支付宝") }
            method.contains("微信") || method.contains("零钱") -> named(active) { it.contains("微信") }
            method.contains("支付宝") || method.contains("余额宝") -> named(active) { it.contains("支付宝") }
            method.contains("现金") -> named(active) { it.contains("现金") }
            method.contains("信用卡") -> active.firstOrNull { it.type == AccountType.CREDIT_CARD || it.name.contains("信用卡") }?.id
            method.contains("银行卡") || method.contains("储蓄卡") || method.contains("借记卡") || method.contains("银行") ->
                active.firstOrNull { it.name.contains("银行") || it.type == AccountType.BANK_CARD }?.id
            else -> null
        }
        if (matched != null) return AccountSuggestion(matched, true)
        val preferredDefault = defaultAccountId?.takeIf { id -> active.any { it.id == id } }
        if (preferredDefault != null) return AccountSuggestion(preferredDefault, false)
        val fallback = when (source) {
            BillSource.WECHAT -> named(active) { it.contains("微信") }
            BillSource.ALIPAY -> named(active) { it.contains("支付宝") }
        } ?: active.firstOrNull()?.id
        return AccountSuggestion(fallback, false)
    }

    fun suggestAccountId(
        paymentMethod: String,
        source: BillSource,
        accounts: List<Account>,
        defaultAccountId: String?,
    ): String? = suggest(paymentMethod, source, accounts, defaultAccountId).accountId

    private fun named(accounts: List<Account>, predicate: (String) -> Boolean): String? {
        return accounts.firstOrNull { predicate(it.name) }?.id
    }
}
