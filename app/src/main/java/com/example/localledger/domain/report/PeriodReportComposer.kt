package com.example.localledger.domain.report

import com.example.localledger.domain.budget.BudgetMath
import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DailyTotal
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.money.MinorText
import com.example.localledger.domain.money.RatioMath
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PeriodReportComposer {
    private const val CATEGORY_CAP = 8

    fun compose(facts: ReportFacts): PeriodReport {
        val (start, end) = periodBounds(facts.kind, facts.start)
        val expenseSorted = facts.expenseCategories.sortedWith(
            compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.categoryName },
        )
        val incomeSorted = facts.incomeCategories.sortedWith(
            compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.categoryName },
        )
        val previousTop = facts.previousExpenseCategories
            .sortedWith(compareByDescending<CategoryTotal> { it.amountMinor }.thenBy { it.categoryName })
            .firstOrNull()
            ?.categoryName
        val top = expenseSorted.firstOrNull()
        val filledDays = fillDays(start, end, facts.days)
        val weeks = if (facts.kind == ReportKind.MONTH) weekSlices(filledDays) else emptyList()
        val previousDays = previousPeriod(facts.kind, start)?.let { (from, to) ->
            ChronoUnit.DAYS.between(from, to).toInt() + 1
        } ?: 0
        val priorWord = if (facts.kind == ReportKind.WEEK) "上周" else "上月"
        val spikeDays = filledDays
            .filter { ReportSpikes.dayIsSpike(it.expenseMinor, facts.previousExpenseMinor, previousDays) }
            .sortedByDescending { it.expenseMinor }
            .take(2)
        val categorySpike = categorySpike(expenseSorted, facts.previousExpenseCategories)
        val budget = if (facts.kind == ReportKind.MONTH) {
            chosenBudget(facts.budgets, facts.expenseMinor, expenseSorted)
        } else {
            null
        }
        val budgetSentence = budget?.let { budgetProse(it) }
        val prose = ReportSummary.prose(
            ProseInput(
                kind = facts.kind,
                incomeMinor = facts.incomeMinor,
                expenseMinor = facts.expenseMinor,
                previousExpenseMinor = facts.previousExpenseMinor,
                topCategoryName = top?.categoryName,
                previousTopCategoryName = previousTop,
                spikeDayLabel = spikeDays.firstOrNull()?.let { dayLabel(it.date) },
                budgetSentence = budgetSentence,
            ),
        )
        val balance = try {
            Math.subtractExact(facts.incomeMinor, facts.expenseMinor)
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }
        val previousBalance = try {
            Math.subtractExact(facts.previousIncomeMinor, facts.previousExpenseMinor)
        } catch (error: ArithmeticException) {
            throw LedgerException.ArithmeticOverflow()
        }
        val expenseComparison = ReportSummary.movementLine(
            subject = "支出",
            previousWord = priorWord,
            delta = PeriodDelta(facts.expenseMinor, facts.previousExpenseMinor),
            currencyCode = facts.currencyCode,
            zeroPrevious = "${priorWord}没有支出",
        )
        val incomeComparison = ReportSummary.movementLine(
            subject = "收入",
            previousWord = priorWord,
            delta = PeriodDelta(facts.incomeMinor, facts.previousIncomeMinor),
            currencyCode = facts.currencyCode,
            zeroPrevious = "${priorWord}没有收入",
        )
        val balanceComparison = ReportSummary.movementLine(
            subject = "结余",
            previousWord = priorWord,
            delta = PeriodDelta(balance, previousBalance),
            currencyCode = facts.currencyCode,
            zeroPrevious = "${priorWord}结余为零",
        )
        val highlights = buildHighlights(
            facts = facts,
            largest = facts.largestExpense,
            frequent = facts.expenseCounts.maxWithOrNull(
                compareBy<com.example.localledger.domain.model.CategoryFrequency> { it.count }
                    .thenBy { it.amountMinor },
            ),
            spikeDays = spikeDays,
            categorySpike = categorySpike,
            budget = budget,
            priorWord = priorWord,
        )
        val label = periodLabel(facts.kind, start)
        val currencyLabel = currencyLabel(facts.currencyCode)
        val ledgerName = facts.ledgerName.ifBlank { "未命名账本" }
        val report = PeriodReport(
            kind = facts.kind,
            periodLabel = label,
            ledgerName = ledgerName,
            currencyCode = facts.currencyCode,
            currencyLabel = currencyLabel,
            prose = prose,
            incomeMinor = facts.incomeMinor,
            expenseMinor = facts.expenseMinor,
            balanceMinor = balance,
            expenseComparison = expenseComparison,
            incomeComparison = incomeComparison,
            balanceComparison = balanceComparison,
            expenseCategories = expenseSorted.take(CATEGORY_CAP),
            incomeCategories = incomeSorted.take(CATEGORY_CAP),
            days = filledDays,
            weeks = weeks,
            highlights = highlights,
            shareText = "",
            startDate = start,
            endDate = end,
        )
        return report.copy(shareText = shareText(report, top, facts.largestExpense))
    }

    private fun shareText(
        report: PeriodReport,
        top: CategoryTotal?,
        largest: com.example.localledger.domain.model.LargestExpense?,
    ): String = buildString {
        append("牛牛记账")
        append(if (report.kind == ReportKind.WEEK) "周报" else "月报")
        append('\n')
        append(report.ledgerName)
        append(" · ")
        append(report.currencyLabel)
        append('\n')
        append(report.periodLabel)
        append("\n\n")
        append(report.prose)
        append("\n\n")
        append("收入 ")
        append(MinorText.format(report.incomeMinor, report.currencyCode))
        append('\n')
        append("支出 ")
        append(MinorText.format(report.expenseMinor, report.currencyCode))
        append('\n')
        append("结余 ")
        append(MinorText.format(report.balanceMinor, report.currencyCode))
        append('\n')
        append(report.incomeComparison)
        append('\n')
        append(report.expenseComparison)
        append('\n')
        append(report.balanceComparison)
        if (top != null) {
            append('\n')
            append("最大类别 ")
            append(top.categoryName)
            append(' ')
            append(MinorText.format(top.amountMinor, report.currencyCode))
        }
        if (largest != null) {
            append('\n')
            append("最大一笔 ")
            append(largest.categoryName)
            append(' ')
            append(MinorText.format(largest.amountMinor, report.currencyCode))
        }
    }

    private fun buildHighlights(
        facts: ReportFacts,
        largest: com.example.localledger.domain.model.LargestExpense?,
        frequent: com.example.localledger.domain.model.CategoryFrequency?,
        spikeDays: List<DailyTotal>,
        categorySpike: Pair<CategoryTotal, Long>?,
        budget: ChosenBudget?,
        priorWord: String,
    ): List<ReportHighlight> {
        val lines = ArrayList<ReportHighlight>(6)
        if (largest != null) {
            val note = clipNote(largest.note)
            val body = buildString {
                append(dayLabel(largest.date))
                append(" · ")
                append(largest.categoryName)
                append(" · ")
                append(MinorText.format(largest.amountMinor, facts.currencyCode))
                if (note != null) {
                    append(" · ")
                    append(note)
                }
            }
            lines += ReportHighlight(HighlightKind.LARGEST, "最大一笔", body)
        }
        if (frequent != null && frequent.count > 0) {
            lines += ReportHighlight(
                kind = HighlightKind.FREQUENT,
                title = "最常记录",
                body = "${frequent.categoryName} · ${frequent.count} 笔 · ${MinorText.format(frequent.amountMinor, facts.currencyCode)}",
            )
        }
        if (budget != null) {
            lines += ReportHighlight(HighlightKind.BUDGET, "预算", budgetDetail(budget, facts.currencyCode))
        }
        spikeDays.forEach { day ->
            lines += ReportHighlight(
                kind = HighlightKind.SPIKE,
                title = "支出偏高",
                body = "${dayLabel(day.date)} ${MinorText.format(day.expenseMinor, facts.currencyCode)}，高于${priorWord}日均",
            )
        }
        if (categorySpike != null) {
            val (category, previous) = categorySpike
            val delta = Math.subtractExact(category.amountMinor, previous)
            lines += ReportHighlight(
                kind = HighlightKind.SPIKE,
                title = "支出偏高",
                body = "${category.categoryName}较${priorWord}增加 ${MinorText.format(delta, facts.currencyCode)}",
            )
        }
        return lines
    }

    private fun categorySpike(
        current: List<CategoryTotal>,
        previous: List<CategoryTotal>,
    ): Pair<CategoryTotal, Long>? {
        val previousById = previous.associate { it.categoryId to it.amountMinor }
        return current
            .mapNotNull { category ->
                val prior = previousById[category.categoryId] ?: return@mapNotNull null
                if (!ReportSpikes.categoryIsSpike(category.amountMinor, prior)) return@mapNotNull null
                category to prior
            }
            .maxByOrNull { (category, prior) -> Math.subtractExact(category.amountMinor, prior) }
    }

    private fun chosenBudget(
        budgets: List<Budget>,
        expenseMinor: Long,
        categories: List<CategoryTotal>,
    ): ChosenBudget? {
        val valid = budgets.filter { it.amountMinor > 0L }
        if (valid.isEmpty()) return null
        val spentByCategory = categories.associate { it.categoryId to it.amountMinor }
        val total = valid.firstOrNull { it.categoryId == null }
        val chosen = total ?: valid.maxByOrNull { budget ->
            val spent = spentOf(budget, expenseMinor, spentByCategory)
            RatioMath.ratio(spent, budget.amountMinor)
        } ?: return null
        val spent = spentOf(chosen, expenseMinor, spentByCategory)
        val figures = try {
            BudgetMath.figures(chosen.amountMinor, spent)
        } catch (error: LedgerException) {
            return null
        }
        return ChosenBudget(
            total = chosen.categoryId == null,
            name = chosen.categoryName ?: "总预算",
            figures = figures,
        )
    }

    private fun spentOf(budget: Budget, expenseMinor: Long, byCategory: Map<String, Long>): Long =
        if (budget.categoryId == null) expenseMinor else byCategory[budget.categoryId] ?: 0L

    private fun budgetProse(budget: ChosenBudget): String {
        val name = if (budget.total) "总预算" else "${budget.name}预算"
        val percent = percentText(
            budget.figures.ratio.movePointRight(2).setScale(1, RoundingMode.HALF_UP),
        )
        return if (budget.figures.overspent) "${name}已超出。" else "${name}已用 $percent。"
    }

    private fun budgetDetail(budget: ChosenBudget, currencyCode: String): String {
        val name = if (budget.total) "总预算" else "${budget.name}预算"
        val percent = percentText(
            budget.figures.ratio.movePointRight(2).setScale(1, RoundingMode.HALF_UP),
        )
        return if (budget.figures.overspent) {
            val over = Math.subtractExact(budget.figures.spentMinor, budget.figures.limitMinor)
            "${name}已超出 ${MinorText.format(over, currencyCode)}，目前 $percent"
        } else {
            "${name}已用 $percent，剩余 ${MinorText.format(budget.figures.remainingMinor, currencyCode)}"
        }
    }

    private fun fillDays(start: LocalDate, end: LocalDate, actual: List<DailyTotal>): List<DailyTotal> {
        if (end.isBefore(start)) return emptyList()
        val byDate = actual.associateBy { it.date }
        val result = ArrayList<DailyTotal>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            result += byDate[cursor] ?: DailyTotal(cursor, incomeMinor = 0, expenseMinor = 0)
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun weekSlices(days: List<DailyTotal>): List<WeekSlice> {
        if (days.isEmpty()) return emptyList()
        return days.groupBy { com.example.localledger.domain.model.StatisticsPeriod.Week.containing(it.date).weekStart }
            .toSortedMap()
            .map { (_, items) ->
                val from = items.minOf { it.date }
                val to = items.maxOf { it.date }
                WeekSlice(
                    label = shortDateSpan(from, to),
                    incomeMinor = items.fold(0L) { acc, day -> Math.addExact(acc, day.incomeMinor) },
                    expenseMinor = items.fold(0L) { acc, day -> Math.addExact(acc, day.expenseMinor) },
                )
            }
    }

    private fun dayLabel(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    private fun clipNote(note: String?): String? {
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.length <= 18) trimmed else trimmed.take(18) + "…"
    }

    private fun currencyLabel(code: String): String {
        if (!CurrencyCatalog.contains(code)) return code
        val spec = CurrencyCatalog.of(code)
        return "${spec.label}（${spec.code}）"
    }
}

private data class ChosenBudget(
    val total: Boolean,
    val name: String,
    val figures: com.example.localledger.domain.budget.BudgetFigures,
)
