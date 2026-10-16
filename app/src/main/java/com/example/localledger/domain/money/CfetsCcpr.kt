package com.example.localledger.domain.money

import com.example.localledger.domain.error.LedgerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 中国外汇交易中心公布的人民币汇率中间价。
 * `USD/CNY` 表示 1 外币兑多少人民币；`100JPY/CNY` 表示 100 外币兑多少人民币；
 * `CNY/KRW` 表示 1 人民币兑多少外币。结果统一成「1 人民币可兑换的外币数量」。
 */
object CfetsCcpr {
    val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")
    const val SOURCE = "中国外汇交易中心人民币汇率中间价"

    private const val SCALE = 16
    private val PAIR = Regex("""^(\d+)?([A-Z]{3})/(\d+)?([A-Z]{3})$""")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatest(body: String): CnyQuotedRates {
        val root = parseObject(body)
        val published = root["data"]?.jsonObject?.string("lastDate")
            ?: throw LedgerException.ExchangeRateUnavailable()
        val rates = LinkedHashMap<String, BigDecimal>()
        root.array("records").forEach { element ->
            val record = element.jsonObject
            val pair = record.string("vrtEName") ?: return@forEach
            val price = record["price"].decimal() ?: return@forEach
            putPair(rates, pair, price)
        }
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        return CnyQuotedRates(rates, publishedAtMillis(published), SOURCE)
    }

    fun parseHistory(body: String): CnyQuotedRates {
        val root = parseObject(body)
        val data = root["data"]?.jsonObject ?: throw LedgerException.ExchangeRateUnavailable()
        val pairs = data.arrayOrNull("head") ?: data.arrayOrNull("searchlist")
            ?: throw LedgerException.ExchangeRateUnavailable()
        val latest = root.array("records").firstOrNull()?.jsonObject
            ?: throw LedgerException.ExchangeRateUnavailable()
        val date = latest.string("date") ?: throw LedgerException.ExchangeRateUnavailable()
        val values = latest.array("values")
        if (values.size != pairs.size) throw LedgerException.ExchangeRateUnavailable()
        val rates = LinkedHashMap<String, BigDecimal>()
        pairs.forEachIndexed { index, pairElement ->
            val pair = pairElement.jsonPrimitive.contentOrNull ?: return@forEachIndexed
            val price = values[index].decimal() ?: return@forEachIndexed
            putPair(rates, pair, price)
        }
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        return CnyQuotedRates(rates, publishedAtMillis(date), SOURCE)
    }

    /** 交易中心在每个工作日约 9:15（北京时间）公布。只有日期时按 9:15 计。 */
    fun publishedAtMillis(text: String): Long {
        val trimmed = text.trim()
        val dateTime = runCatching { LocalDateTime.parse(trimmed, DATE_TIME) }.getOrNull()
        if (dateTime != null) return dateTime.atZone(BEIJING).toInstant().toEpochMilli()
        val date = runCatching { LocalDate.parse(trimmed.take(10)) }.getOrNull()
            ?: throw LedgerException.ExchangeRateUnavailable()
        return date.atTime(9, 15).atZone(BEIJING).toInstant().toEpochMilli()
    }

    private fun putPair(rates: MutableMap<String, BigDecimal>, pair: String, price: BigDecimal) {
        val (code, perCny) = foreignPerOneCny(pair, price) ?: return
        if (code !in rates) rates[code] = perCny
    }

    /** @return 币种 to 1 人民币可兑换的该币数量。目录外的货币对会被忽略。 */
    fun foreignPerOneCny(pair: String, price: BigDecimal): Pair<String, BigDecimal>? {
        if (price.signum() <= 0) return null
        val match = PAIR.matchEntire(pair.trim().uppercase()) ?: return null
        val leftQty = match.groupValues[1].toBigDecimalOrNull() ?: BigDecimal.ONE
        val left = match.groupValues[2]
        val rightQty = match.groupValues[3].toBigDecimalOrNull() ?: BigDecimal.ONE
        val right = match.groupValues[4]
        if (leftQty.signum() <= 0 || rightQty.signum() <= 0) return null
        return when {
            right == "CNY" && left != "CNY" && CurrencyCatalog.contains(left) ->
                left to leftQty.divide(price, SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
            left == "CNY" && right != "CNY" && CurrencyCatalog.contains(right) ->
                right to price.multiply(rightQty).divide(leftQty, SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
            else -> null
        }
    }

    private fun parseObject(body: String): JsonObject =
        try {
            json.parseToJsonElement(body).jsonObject
        } catch (error: Exception) {
            throw LedgerException.ExchangeRateUnavailable(error)
        }

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: throw LedgerException.ExchangeRateUnavailable()

    private fun JsonObject.arrayOrNull(name: String): JsonArray? = this[name]?.jsonArray

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonElement?.decimal(): BigDecimal? {
        if (this == null || this is JsonNull) return null
        val text = jsonPrimitive.contentOrNull?.trim().orEmpty()
        if (text.isEmpty() || text == "---") return null
        return text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }
}

data class CnyQuotedRates(
    /** 1 人民币可兑换的外币数量。 */
    val foreignPerOneCny: Map<String, BigDecimal>,
    val publishedAtMillis: Long,
    val source: String,
)
