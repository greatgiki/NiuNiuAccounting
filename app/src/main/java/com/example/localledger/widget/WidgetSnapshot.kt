package com.example.localledger.widget

import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.widget.WidgetPrivacy
import com.example.localledger.ui.common.MoneyFormatter

data class WidgetSnapshot(
    val currencyCode: String,
    val today: IncomeExpenseSummary,
    val month: IncomeExpenseSummary,
    val maskAmounts: Boolean,
) {
    companion object {
        fun empty(maskAmounts: Boolean) = WidgetSnapshot(
            currencyCode = "CNY",
            today = IncomeExpenseSummary(0, 0),
            month = IncomeExpenseSummary(0, 0),
            maskAmounts = maskAmounts,
        )
    }
}

object WidgetAmounts {
    fun text(minor: Long, currencyCode: String, mask: Boolean): String {
        return if (mask) WidgetPrivacy.MASK else MoneyFormatter.format(minor, currencyCode)
    }
}
