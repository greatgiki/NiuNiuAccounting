package com.example.localledger.domain.report

import com.example.localledger.domain.money.MinorText

data class ProseInput(
    val kind: ReportKind,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val previousExpenseMinor: Long,
    val topCategoryName: String?,
    val previousTopCategoryName: String?,
    val spikeDayLabel: String?,
    val budgetSentence: String?,
)

object ReportSummary {
    fun prose(input: ProseInput): String {
        val period = if (input.kind == ReportKind.WEEK) "本周" else "本月"
        val prior = if (input.kind == ReportKind.WEEK) "上周" else "上月"
        val noSpend = input.expenseMinor == 0L && input.previousExpenseMinor == 0L
        if (input.incomeMinor == 0L && noSpend && input.topCategoryName.isNullOrBlank()) {
            return "${period}没有收支记录。"
        }
        val expenseClause = expenseClause(period, prior, input.expenseMinor, input.previousExpenseMinor)
        val categoryClause = categoryClause(input.topCategoryName, input.previousTopCategoryName)
        val head = when {
            expenseClause != null && categoryClause != null -> "$expenseClause，$categoryClause。"
            expenseClause != null -> "$expenseClause。"
            categoryClause != null -> "$categoryClause。"
            else -> ""
        }
        val tail = buildString {
            input.spikeDayLabel?.let { append("${it}支出明显高于${prior}日均。") }
            input.budgetSentence?.let { append(it) }
        }
        return head + tail
    }

    fun movementLine(
        subject: String,
        previousWord: String,
        delta: PeriodDelta,
        currencyCode: String,
        zeroPrevious: String,
    ): String {
        if (delta.currentMinor == delta.previousMinor) return "${subject}与${previousWord}持平"
        if (delta.previousMinor == 0L) return zeroPrevious
        val label = delta.magnitudePercentLabel()
        if (delta.previousMinor > 0L && (label == null || label == "0%")) {
            return "${subject}与${previousWord}基本持平"
        }
        val amount = MinorText.format(magnitudeMinor(delta.absoluteMinor), currencyCode)
        val percent = label?.takeUnless { it == "0%" }?.let { "（$it）" }.orEmpty()
        val verb = if (delta.absoluteMinor < 0L) "少" else "多"
        return "${subject}较${previousWord}$verb $amount$percent"
    }

    private fun expenseClause(
        period: String,
        prior: String,
        current: Long,
        previous: Long,
    ): String? {
        if (current == 0L && previous == 0L) return null
        if (current == 0L && previous > 0L) return "${period}没有支出"
        if (previous <= 0L && current > 0L) return "${period}有支出，${prior}没有"
        if (current == previous) return "${period}支出与${prior}持平"
        val label = PeriodDelta(current, previous).magnitudePercentLabel()
        if (label == null || label == "0%") return "${period}支出与${prior}基本持平"
        return if (current < previous) {
            "${period}支出比${prior}少 $label"
        } else {
            "${period}支出比${prior}多 $label"
        }
    }

    private fun categoryClause(top: String?, previousTop: String?): String? {
        if (top.isNullOrBlank()) return null
        return if (top == previousTop) "${top}仍是最大类别" else "${top}是最大支出类别"
    }
}
