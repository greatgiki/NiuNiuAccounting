package com.example.localledger.domain.ai

import androidx.compose.runtime.Immutable
import com.example.localledger.domain.budget.BudgetMath
import com.example.localledger.domain.model.Budget
import com.example.localledger.domain.model.CategoryTotal
import com.example.localledger.domain.model.DateTimeRange
import com.example.localledger.domain.model.IncomeExpenseSummary
import com.example.localledger.domain.model.TransactionDetail
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.domain.time.DateRangeResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

interface LedgerQuerySource {
    suspend fun summary(ledgerId: String, range: DateTimeRange): IncomeExpenseSummary
    suspend fun categories(ledgerId: String, range: DateTimeRange, income: Boolean): List<CategoryTotal>
    suspend fun topTransactions(
        ledgerId: String,
        range: DateTimeRange,
        type: TransactionType?,
        limit: Int,
    ): List<TransactionDetail>
    suspend fun budgets(ledgerId: String, month: YearMonth): List<Budget>
}

@Immutable
data class QueryCitation(
    val periodLabel: String,
    val queryLabel: String,
    val fact: String,
)

data class ToolExecution(
    val modelContent: String,
    val citation: QueryCitation?,
)

object LedgerToolNames {
    const val TOTALS = "period_totals"
    const val CATEGORIES = "category_breakdown"
    const val TOP = "top_transactions"
    const val COMPARE = "compare_periods"
    const val BUDGET = "budget_status"

    const val CATEGORY_LIMIT = 12
    const val TOP_LIMIT = 8

    fun label(name: String): String = when (name) {
        TOTALS -> "收支合计"
        CATEGORIES -> "分类汇总"
        TOP -> "金额较高的流水"
        COMPARE -> "期间对比"
        BUDGET -> "预算"
        else -> "查询"
    }
}

object LedgerTools {
    fun specs() = listOf(
        spec(
            LedgerToolNames.TOTALS,
            "查询一段时间的收入、支出和结余。金额只能用返回值。",
            periodProperties(required = listOf("period")),
        ),
        spec(
            LedgerToolNames.CATEGORIES,
            "按分类汇总收入或支出，最多返回 12 类。",
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("period", periodProperty())
                    put("start", dateProperty("period 为 custom 时的开始日期"))
                    put("end", dateProperty("period 为 custom 时的结束日期"))
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("EXPENSE"))
                            add(kotlinx.serialization.json.JsonPrimitive("INCOME"))
                        })
                        put("description", "EXPENSE 支出，INCOME 收入")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "1 到 12，默认 8")
                    })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("period")) })
            },
        ),
        spec(
            LedgerToolNames.TOP,
            "金额最高的几笔流水。不返回账户名和卡号。",
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("period", periodProperty())
                    put("start", dateProperty("period 为 custom 时的开始日期"))
                    put("end", dateProperty("period 为 custom 时的结束日期"))
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("EXPENSE"))
                            add(kotlinx.serialization.json.JsonPrimitive("INCOME"))
                        })
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "1 到 8，默认 5")
                    })
                })
                put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("period")) })
            },
        ),
        spec(
            LedgerToolNames.COMPARE,
            "对比两个时间段的收入和支出。",
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("first", periodProperty())
                    put("second", periodProperty())
                    put("first_start", dateProperty("first 为 custom 时的开始"))
                    put("first_end", dateProperty("first 为 custom 时的结束"))
                    put("second_start", dateProperty("second 为 custom 时的开始"))
                    put("second_end", dateProperty("second 为 custom 时的结束"))
                })
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("first"))
                    add(kotlinx.serialization.json.JsonPrimitive("second"))
                })
            },
        ),
        spec(
            LedgerToolNames.BUDGET,
            "查询某月预算的上限、已用和剩余。",
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("month", buildJsonObject {
                        put("type", "string")
                        put("description", "YYYY-MM，不填则用本月")
                    })
                })
            },
        ),
    )

    private fun spec(name: String, description: String, parameters: JsonObject) =
        AiToolSpec(name = name, description = description, parameters = parameters)

    private fun periodProperties(required: List<String>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("period", periodProperty())
            put("start", dateProperty("period 为 custom 时的开始日期 YYYY-MM-DD"))
            put("end", dateProperty("period 为 custom 时的结束日期 YYYY-MM-DD"))
        })
        put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
    }

    private fun periodProperty() = buildJsonObject {
        put("type", "string")
        put("description", "today、yesterday、this_week、last_week、this_month、last_month、this_year、last_year、custom")
        put("enum", buildJsonArray {
            listOf(
                "today", "yesterday", "this_week", "last_week",
                "this_month", "last_month", "this_year", "last_year", "custom",
            ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        })
    }

    private fun dateProperty(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    fun encode(payload: JsonObject): String = payload.toString()
}

@Singleton
class LedgerToolExecutor @Inject constructor(
    private val source: LedgerQuerySource,
    private val ranges: DateRangeResolver,
    private val clock: Clock,
) {
    suspend fun execute(ledgerId: String, currencyCode: String, call: AiToolCall): ToolExecution {
        val arguments = parseArguments(call.arguments)
            ?: return error("参数不是 JSON 对象")
        return when (call.name) {
            LedgerToolNames.TOTALS -> totals(ledgerId, currencyCode, arguments)
            LedgerToolNames.CATEGORIES -> categories(ledgerId, currencyCode, arguments)
            LedgerToolNames.TOP -> top(ledgerId, currencyCode, arguments)
            LedgerToolNames.COMPARE -> compare(ledgerId, currencyCode, arguments)
            LedgerToolNames.BUDGET -> budget(ledgerId, currencyCode, arguments)
            else -> error("没有这个查询")
        }
    }

    private suspend fun totals(ledgerId: String, currency: String, arguments: JsonObject): ToolExecution {
        val period = resolve(arguments, "period", "start", "end") ?: return error("无法识别的时间范围")
        val summary = source.summary(ledgerId, period.range(ranges))
        val payload = buildJsonObject {
            put("query", LedgerToolNames.TOTALS)
            put("period", period.label)
            put("currency", currency)
            put("income", AiMoney.major(summary.incomeMinor, currency))
            put("expense", AiMoney.major(summary.expenseMinor, currency))
            put("balance", AiMoney.major(summary.balanceMinor, currency))
        }
        return done(
            payload,
            period.label,
            LedgerToolNames.TOTALS,
            "收入 ${AiMoney.labeled(summary.incomeMinor, currency)}，支出 ${AiMoney.labeled(summary.expenseMinor, currency)}，结余 ${AiMoney.labeled(summary.balanceMinor, currency)}",
        )
    }

    private suspend fun categories(ledgerId: String, currency: String, arguments: JsonObject): ToolExecution {
        val period = resolve(arguments, "period", "start", "end") ?: return error("无法识别的时间范围")
        val income = arguments.string("direction")?.equals("INCOME", ignoreCase = true) == true ||
            arguments.string("type")?.equals("INCOME", ignoreCase = true) == true
        val limit = clamp(arguments.int("limit"), LedgerToolNames.CATEGORY_LIMIT, default = 8)
        val rows = source.categories(ledgerId, period.range(ranges), income)
            .sortedByDescending { it.amountMinor }
        val shown = rows.take(limit)
        val rest = rows.drop(limit)
        val payload = buildJsonObject {
            put("query", LedgerToolNames.CATEGORIES)
            put("period", period.label)
            put("direction", if (income) "INCOME" else "EXPENSE")
            put("currency", currency)
            put("items", buildJsonArray {
                shown.forEach { row ->
                    add(buildJsonObject {
                        put("category", safe(row.categoryName))
                        put("amount", AiMoney.major(row.amountMinor, currency))
                    })
                }
            })
            put("truncated", rest.isNotEmpty())
            if (rest.isNotEmpty()) {
                put("remainder", AiMoney.major(sum(rest), currency))
            }
        }
        val parts = shown.map { "${safe(it.categoryName)} ${AiMoney.labeled(it.amountMinor, currency)}" }.toMutableList()
        if (rest.isNotEmpty()) parts += "其余合计 ${AiMoney.labeled(sum(rest), currency)}"
        val fact = if (parts.isEmpty()) "没有分类数据" else parts.joinToString("、")
        return done(payload, period.label, LedgerToolNames.CATEGORIES, fact)
    }

    private suspend fun top(ledgerId: String, currency: String, arguments: JsonObject): ToolExecution {
        val period = resolve(arguments, "period", "start", "end") ?: return error("无法识别的时间范围")
        val direction = parseDirection(arguments.string("direction") ?: arguments.string("type"))
        val limit = clamp(arguments.int("limit"), LedgerToolNames.TOP_LIMIT, default = 5)
        val rows = source.topTransactions(ledgerId, period.range(ranges), direction, limit)
            .sortedByDescending { it.transaction.baseAmountMinor }
            .take(limit)
        val zone = ranges.zoneId()
        val payload = buildJsonObject {
            put("query", LedgerToolNames.TOP)
            put("period", period.label)
            put("currency", currency)
            put("items", buildJsonArray {
                rows.forEach { detail ->
                    val date = Instant.ofEpochMilli(detail.transaction.transactionAt).atZone(zone).toLocalDate()
                    add(buildJsonObject {
                        put("date", date.toString())
                        put("direction", detail.transaction.type.name)
                        put("category", safe(detail.categoryName ?: typeLabel(detail.transaction.type)))
                        put("amount", AiMoney.major(detail.transaction.baseAmountMinor, currency))
                        put("note", safe(detail.transaction.note.orEmpty()).take(40))
                    })
                }
            })
        }
        val fact = if (rows.isEmpty()) {
            "没有流水"
        } else {
            rows.joinToString("；") { detail ->
                val date = Instant.ofEpochMilli(detail.transaction.transactionAt).atZone(zone).toLocalDate()
                val category = safe(detail.categoryName ?: typeLabel(detail.transaction.type))
                "${date.monthValue}月${date.dayOfMonth}日 $category ${AiMoney.labeled(detail.transaction.baseAmountMinor, currency)}"
            }
        }
        return done(payload, period.label, LedgerToolNames.TOP, fact)
    }

    private suspend fun compare(ledgerId: String, currency: String, arguments: JsonObject): ToolExecution {
        val first = resolve(arguments, "first", "first_start", "first_end") ?: return error("无法识别的时间范围")
        val second = resolve(arguments, "second", "second_start", "second_end") ?: return error("无法识别的时间范围")
        val left = source.summary(ledgerId, first.range(ranges))
        val right = source.summary(ledgerId, second.range(ranges))
        val payload = buildJsonObject {
            put("query", LedgerToolNames.COMPARE)
            put("currency", currency)
            put("first", summaryJson(first.label, left, currency))
            put("second", summaryJson(second.label, right, currency))
        }
        val fact = listOf(first to left, second to right).joinToString("；") { (period, summary) ->
            "${period.label} 收入 ${AiMoney.labeled(summary.incomeMinor, currency)}，支出 ${AiMoney.labeled(summary.expenseMinor, currency)}"
        }
        return done(payload, "${first.label} 与 ${second.label}", LedgerToolNames.COMPARE, fact)
    }

    private suspend fun budget(ledgerId: String, currency: String, arguments: JsonObject): ToolExecution {
        val month = parseMonth(arguments.string("month")) ?: YearMonth.from(today())
        val period = LedgerPeriods.resolve("custom", month.atDay(1).toString(), month.atEndOfMonth().toString(), today())
            ?: return error("无法识别的时间范围")
        val summary = source.summary(ledgerId, period.range(ranges))
        val spentByCategory = source.categories(ledgerId, period.range(ranges), income = false)
            .associate { it.categoryId to it.amountMinor }
        val lines = source.budgets(ledgerId, month).take(8).mapNotNull { budget ->
            budgetLine(budget, summary, spentByCategory, currency)
        }
        val label = "${month.year}年${month.monthValue}月"
        val payload = buildJsonObject {
            put("query", LedgerToolNames.BUDGET)
            put("period", label)
            put("currency", currency)
            put("items", buildJsonArray {
                lines.forEach { line ->
                    add(buildJsonObject {
                        put("name", line.name)
                        put("limit", line.limit)
                        put("spent", line.spent)
                        put("remaining", line.remaining)
                        put("overspent", line.overspent)
                    })
                }
            })
        }
        val fact = if (lines.isEmpty()) "这个月没有预算" else lines.joinToString("；") { it.fact }
        return done(payload, label, LedgerToolNames.BUDGET, fact)
    }

    private fun budgetLine(
        budget: Budget,
        summary: IncomeExpenseSummary,
        spentByCategory: Map<String, Long>,
        currency: String,
    ): BudgetLine? {
        val spent = if (budget.categoryId == null) {
            summary.expenseMinor
        } else {
            spentByCategory[budget.categoryId] ?: 0L
        }
        val figures = try {
            BudgetMath.figures(budget.amountMinor, spent)
        } catch (_: Exception) {
            return null
        }
        val name = safe(budget.categoryName ?: "总预算")
        return BudgetLine(
            name = name,
            limit = AiMoney.major(figures.limitMinor, currency),
            spent = AiMoney.major(figures.spentMinor, currency),
            remaining = AiMoney.major(figures.remainingMinor, currency),
            overspent = figures.overspent,
            fact = "$name 已用 ${AiMoney.labeled(figures.spentMinor, currency)} / ${AiMoney.labeled(figures.limitMinor, currency)}，剩余 ${AiMoney.labeled(figures.remainingMinor, currency)}",
        )
    }

    private fun summaryJson(label: String, summary: IncomeExpenseSummary, currency: String) = buildJsonObject {
        put("period", label)
        put("income", AiMoney.major(summary.incomeMinor, currency))
        put("expense", AiMoney.major(summary.expenseMinor, currency))
        put("balance", AiMoney.major(summary.balanceMinor, currency))
    }

    private fun resolve(arguments: JsonObject, periodKey: String, startKey: String, endKey: String): ResolvedPeriod? =
        LedgerPeriods.resolve(arguments.string(periodKey), arguments.string(startKey), arguments.string(endKey), today())

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ranges.zoneId()))

    private fun parseArguments(text: String): JsonObject? = try {
        Json.parseToJsonElement(text.ifBlank { "{}" }).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun parseMonth(raw: String?): YearMonth? {
        if (raw.isNullOrBlank()) return null
        return try {
            YearMonth.parse(raw.trim().take(7))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDirection(raw: String?): TransactionType? = when (raw?.trim()?.uppercase()) {
        "INCOME", "收入" -> TransactionType.INCOME
        "EXPENSE", "支出" -> TransactionType.EXPENSE
        else -> null
    }

    private fun clamp(raw: Int?, max: Int, default: Int): Int = (raw ?: default).coerceIn(1, max)

    private fun sum(rows: List<CategoryTotal>): Long = try {
        rows.fold(0L) { acc, row -> Math.addExact(acc, row.amountMinor) }
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

    private fun safe(text: String): String = LedgerTextRedactor.redact(text).replace("\n", " ").trim()

    private fun typeLabel(type: TransactionType) = when (type) {
        TransactionType.INCOME -> "收入"
        TransactionType.EXPENSE -> "支出"
        TransactionType.TRANSFER -> "转账"
    }

    private fun done(payload: JsonObject, period: String, query: String, fact: String) = ToolExecution(
        modelContent = LedgerTools.encode(payload),
        citation = QueryCitation(period, LedgerToolNames.label(query), LedgerTextRedactor.redact(fact)),
    )

    private fun error(message: String) = ToolExecution(
        modelContent = LedgerTools.encode(buildJsonObject { put("error", message) }),
        citation = null,
    )

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String): Int? = string(name)?.toIntOrNull()

    private data class BudgetLine(
        val name: String,
        val limit: String,
        val spent: String,
        val remaining: String,
        val overspent: Boolean,
        val fact: String,
    )
}
