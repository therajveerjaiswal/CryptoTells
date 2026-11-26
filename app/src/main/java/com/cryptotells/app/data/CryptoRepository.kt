package com.cryptotells.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CryptoRepository {

    companion object {
        private const val BASE_URL = "https://api.coingecko.com/api/v3"

        val POPULAR_CURRENCIES = mapOf(
            "usd" to "US Dollar",
            "inr" to "Indian Rupee",
            "eur" to "Euro",
            "gbp" to "British Pound",
            "jpy" to "Japanese Yen",
            "aud" to "Australian Dollar",
            "cad" to "Canadian Dollar",
            "sgd" to "Singapore Dollar",
            "aed" to "UAE Dirham",
            "chf" to "Swiss Franc",
            "cny" to "Chinese Yuan",
            "krw" to "South Korean Won",
            "brl" to "Brazilian Real",
            "mxn" to "Mexican Peso",
            "zar" to "South African Rand",
            "try" to "Turkish Lira",
            "idr" to "Indonesian Rupiah",
            "php" to "Philippine Peso",
            "thb" to "Thai Baht",
            "vnd" to "Vietnamese Dong",
            "ngn" to "Nigerian Naira",
            "pkr" to "Pakistani Rupee",
            "bdt" to "Bangladeshi Taka"
        )
    }

    private suspend fun getJson(endpoint: String): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CryptoTells-Android/1.0")
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("API error ($code): $body")
        }
        body
    }

    suspend fun fetchSupportedCurrencies(): List<FiatCurrency> {
        val raw = getJson("/simple/supported_vs_currencies")
        val arr = JSONArray(raw)
        val all = mutableListOf<FiatCurrency>()
        for (i in 0 until arr.length()) {
            val code = arr.getString(i).lowercase()
            val label = POPULAR_CURRENCIES[code] ?: code.uppercase()
            all.add(FiatCurrency(code = code, name = label))
        }
        // Put popular currencies first, then alphabetical
        val popularOrder = POPULAR_CURRENCIES.keys.toList()
        return all.sortedWith(
            compareBy<FiatCurrency>(
                { val idx = popularOrder.indexOf(it.code); if (idx == -1) Int.MAX_VALUE else idx },
                { it.code }
            )
        )
    }

    suspend fun fetchTopCoins(vsCurrency: String, perPage: Int = 50): List<CryptoCoin> {
        val encodedVs = URLEncoder.encode(vsCurrency.lowercase(), "UTF-8")
        val raw = getJson(
            "/coins/markets?vs_currency=$encodedVs&order=market_cap_desc&per_page=$perPage&page=1&sparkline=false"
        )
        val arr = JSONArray(raw)
        val list = mutableListOf<CryptoCoin>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                CryptoCoin(
                    id = obj.getString("id"),
                    symbol = obj.getString("symbol").uppercase(),
                    name = obj.getString("name"),
                    currentPrice = obj.optDouble("current_price").takeIf { !it.isNaN() },
                    priceChange24hPct = obj.optDouble("price_change_percentage_24h").takeIf { !it.isNaN() },
                    marketCap = obj.optDouble("market_cap").takeIf { !it.isNaN() },
                    marketCapRank = obj.optInt("market_cap_rank", 0).takeIf { it > 0 }
                )
            )
        }
        return list
    }

    suspend fun searchCoins(query: String): List<CryptoCoin> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = getJson("/search?query=$encoded")
        val root = JSONObject(raw)
        val coins = root.optJSONArray("coins") ?: JSONArray()
        val list = mutableListOf<CryptoCoin>()
        for (i in 0 until minOf(coins.length(), 40)) {
            val obj = coins.getJSONObject(i)
            list.add(
                CryptoCoin(
                    id = obj.getString("id"),
                    symbol = obj.getString("symbol").uppercase(),
                    name = obj.getString("name"),
                    marketCapRank = obj.optInt("market_cap_rank", 0).takeIf { it > 0 }
                )
            )
        }
        return list
    }

    suspend fun fetchQuote(coinId: String, vsCurrency: String): QuoteResult {
        val vs = vsCurrency.lowercase()
        val encodedId = URLEncoder.encode(coinId, "UTF-8")
        val encodedVs = URLEncoder.encode(vs, "UTF-8")
        val raw = getJson(
            "/simple/price?ids=$encodedId&vs_currencies=$encodedVs&include_24hr_change=true&include_market_cap=true&include_last_updated_at=true"
        )
        val root = JSONObject(raw)
        val coinObj = root.optJSONObject(coinId)
            ?: throw RuntimeException("No price data returned for $coinId")
        if (!coinObj.has(vs)) {
            throw RuntimeException("Currency $vs not available for $coinId")
        }
        return QuoteResult(
            coinId = coinId,
            vsCurrency = vs,
            unitPrice = coinObj.getDouble(vs),
            change24hPct = coinObj.optDouble("${vs}_24h_change").takeIf { !it.isNaN() },
            marketCap = coinObj.optDouble("${vs}_market_cap").takeIf { !it.isNaN() },
            lastUpdatedEpochSec = coinObj.optLong("last_updated_at", 0L).takeIf { it > 0L }
        )
    }
}
