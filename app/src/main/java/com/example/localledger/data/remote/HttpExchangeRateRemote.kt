package com.example.localledger.data.remote

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.money.CfetsCcpr
import com.example.localledger.domain.money.CnyQuotedRates
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.money.ExchangeRateAssembly
import com.example.localledger.domain.repository.ExchangeRateRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpExchangeRateRemote @Inject constructor(
    private val clock: Clock,
) : ExchangeRateRemote {
    override suspend fun fetch(baseCode: String): ExchangeRateSnapshot = withContext(Dispatchers.IO) {
        val base = CurrencyCatalog.of(baseCode).code
        ExchangeRateAssembly(
            cfets = { loadCfets() },
            supplement = { loadJsDelivr() },
            gapFillers = listOf({ loadPerCny(OPEN_ER_CNY, "open.er-api.com") }, { loadFrankfurterPerCny() }),
            direct = listOf({ loadFrankfurter(base) }, { loadOpenEr(base) }),
        ).fetch(base)
    }

    private fun loadCfets(): CnyQuotedRates =
        try {
            CfetsCcpr.parseLatest(httpGet(CFETS_LATEST, CHINAMONEY_REFERER))
        } catch (error: CancellationException) {
            throw error
        } catch (latest: Exception) {
            try {
                CfetsCcpr.parseHistory(httpGet(CFETS_HISTORY, CHINAMONEY_REFERER))
            } catch (error: CancellationException) {
                throw error
            } catch (history: Exception) {
                latest.addSuppressed(history)
                throw latest
            }
        }

    private fun loadJsDelivr(): CnyQuotedRates {
        val root = json.parseToJsonElement(httpGet(JSDELIVR)).jsonObject
        val date = root.string("date") ?: throw LedgerException.ExchangeRateUnavailable()
        val rates = readRates(root["cny"]?.jsonObject)
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        val published = LocalDate.parse(date).atStartOfDay(CfetsCcpr.BEIJING).toInstant().toEpochMilli()
        return CnyQuotedRates(rates, published, JSDELIVR_SOURCE)
    }

    private fun loadFrankfurterPerCny(): CnyQuotedRates {
        val snapshot = loadFrankfurter("CNY")
        return CnyQuotedRates(snapshot.quotesPerUnit, snapshot.fetchedAt, snapshot.source)
    }

    private fun loadPerCny(url: String, source: String): CnyQuotedRates {
        val snapshot = loadOpenErStyle(url, "CNY", source)
        return CnyQuotedRates(snapshot.quotesPerUnit, snapshot.fetchedAt, snapshot.source)
    }

    private fun loadFrankfurter(base: String): ExchangeRateSnapshot {
        val root = json.parseToJsonElement(httpGet("$FRANKFURTER?from=$base")).jsonObject
        val rates = readRates(root["rates"]?.jsonObject)
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        val date = root.string("date")
        val published = if (date != null) {
            LocalDate.parse(date).atStartOfDay(CfetsCcpr.BEIJING).toInstant().toEpochMilli()
        } else {
            clock.millis()
        }
        return ExchangeRateSnapshot(
            baseCode = root.string("base") ?: base,
            quotesPerUnit = rates,
            fetchedAt = published,
            source = "Frankfurter",
        )
    }

    private fun loadOpenEr(base: String): ExchangeRateSnapshot =
        loadOpenErStyle("$OPEN_ER/$base", base, "open.er-api.com")

    private fun loadOpenErStyle(url: String, fallbackBase: String, source: String): ExchangeRateSnapshot {
        val root = json.parseToJsonElement(httpGet(url)).jsonObject
        if (root.string("result") != "success") throw LedgerException.ExchangeRateUnavailable()
        val rates = readRates(root["rates"]?.jsonObject)
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        val unix = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull
        return ExchangeRateSnapshot(
            baseCode = root.string("base_code") ?: fallbackBase,
            quotesPerUnit = rates,
            fetchedAt = unix?.times(1_000) ?: clock.millis(),
            source = source,
        )
    }

    private fun readRates(rates: JsonObject?): Map<String, BigDecimal> {
        if (rates == null) return emptyMap()
        return rates.mapNotNull { (rawCode, value) ->
            val code = rawCode.uppercase(Locale.ROOT)
            if (!CurrencyCatalog.contains(code) || code == "CNY") return@mapNotNull null
            val number = value.jsonPrimitive.contentOrNull?.toBigDecimalOrNull() ?: return@mapNotNull null
            if (number.signum() <= 0) return@mapNotNull null
            code to number
        }.toMap()
    }

    private fun httpGet(url: String, referer: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NiuNiuAccounting/1.0")
            if (referer != null) setRequestProperty("Referer", referer)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) throw LedgerException.ExchangeRateUnavailable()
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val CFETS_LATEST = "https://www.chinamoney.com.cn/r/cms/www/chinamoney/data/fx/ccpr.json"
        const val CFETS_HISTORY =
            "https://www.chinamoney.com.cn/ags/ms/cm-u-bk-ccpr/CcprHisNew?pageNum=1&pageSize=1"
        const val CHINAMONEY_REFERER = "https://www.chinamoney.com.cn/chinese/bkccpr/"
        const val JSDELIVR =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/cny.min.json"
        const val JSDELIVR_SOURCE = "currency-api（jsDelivr）"
        const val FRANKFURTER = "https://api.frankfurter.dev/v1/latest"
        const val OPEN_ER = "https://open.er-api.com/v6/latest"
        const val OPEN_ER_CNY = "https://open.er-api.com/v6/latest/CNY"
        val json = Json { ignoreUnknownKeys = true }
    }
}
