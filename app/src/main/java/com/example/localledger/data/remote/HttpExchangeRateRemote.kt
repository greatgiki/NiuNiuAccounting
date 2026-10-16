package com.example.localledger.data.remote

import com.example.localledger.domain.error.LedgerException
import com.example.localledger.domain.model.ExchangeRateSnapshot
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.domain.repository.ExchangeRateRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpExchangeRateRemote @Inject constructor(
    private val clock: Clock,
) : ExchangeRateRemote {
    override suspend fun fetch(baseCode: String): ExchangeRateSnapshot = withContext(Dispatchers.IO) {
        val base = CurrencyCatalog.of(baseCode).code
        try {
            fetchFrankfurter(base)
        } catch (error: CancellationException) {
            throw error
        } catch (primary: Exception) {
            try {
                fetchOpenErApi(base)
            } catch (error: CancellationException) {
                throw error
            } catch (fallback: Exception) {
                throw LedgerException.ExchangeRateUnavailable(fallback)
            }
        }
    }

    private fun fetchFrankfurter(base: String): ExchangeRateSnapshot {
        val body = httpGet("https://api.frankfurter.app/latest?from=$base")
        val root = json.parseToJsonElement(body).jsonObject
        val rates = readRates(root["rates"]?.jsonObject)
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        return ExchangeRateSnapshot(
            baseCode = root.string("base") ?: base,
            quotesPerUnit = rates,
            fetchedAt = clock.millis(),
            source = "Frankfurter",
        )
    }

    private fun fetchOpenErApi(base: String): ExchangeRateSnapshot {
        val body = httpGet("https://open.er-api.com/v6/latest/$base")
        val root = json.parseToJsonElement(body).jsonObject
        if (root.string("result") != "success") throw LedgerException.ExchangeRateUnavailable()
        val rates = readRates(root["rates"]?.jsonObject)
        if (rates.isEmpty()) throw LedgerException.ExchangeRateUnavailable()
        val unix = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull
        return ExchangeRateSnapshot(
            baseCode = root.string("base_code") ?: base,
            quotesPerUnit = rates,
            fetchedAt = unix?.times(1000) ?: clock.millis(),
            source = "open.er-api.com",
        )
    }

    private fun readRates(rates: JsonObject?): Map<String, BigDecimal> {
        if (rates == null) return emptyMap()
        return rates.mapNotNull { (code, value) ->
            if (!CurrencyCatalog.contains(code)) return@mapNotNull null
            val number = value.jsonPrimitive.contentOrNull?.toBigDecimalOrNull()
                ?: value.jsonPrimitive.doubleOrNull?.let { BigDecimal.valueOf(it) }
                ?: return@mapNotNull null
            if (number.signum() <= 0) return@mapNotNull null
            code to number
        }.toMap()
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NiuNiuAccounting/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw LedgerException.ExchangeRateUnavailable()
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
