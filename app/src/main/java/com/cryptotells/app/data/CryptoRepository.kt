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
        private const val COINPAPRIKA_BASE = "https://api.coinpaprika.com/v1"
        private const val FX_URL = "https://open.er-api.com/v6/latest/USD"

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

    private suspend fun getJson(fullUrl: String): String = withContext(Dispatchers.IO) {
        val url = URL(fullUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CryptoTells-Android/1.0")
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("Network error ($code): please try again in a moment.")
        }
        body
    }

    suspend fun fetchUsdRates(): Pair<List<FiatCurrency>, Map<String, Double>> {
        val raw = getJson(FX_URL)
        val root = JSONObject(raw)
        val ratesObj = root.getJSONObject("rates")
        val ratesMap = mutableMapOf<String, Double>()
        val currencies = mutableListOf<FiatCurrency>()

        val keys = ratesObj.keys()
        while (keys.hasNext()) {
            val codeUpper = keys.next()
            val codeLower = codeUpper.lowercase()
            val rate = ratesObj.optDouble(codeUpper)
            if (!rate.isNaN() && rate > 0.0) {
                ratesMap[codeLower] = rate
                currencies.add(
                    FiatCurrency(
                        code = codeLower,
                        name = POPULAR_CURRENCIES[codeLower] ?: codeUpper
                    )
                )
            }
        }

        val popularOrder = POPULAR_CURRENCIES.keys.toList()
        val sorted = currencies.sortedWith(
            compareBy<FiatCurrency>(
                { val idx = popularOrder.indexOf(it.code); if (idx == -1) Int.MAX_VALUE else idx },
                { it.code }
            )
        )
        return sorted to ratesMap
    }

    suspend fun fetchTopCoinsUsd(limit: Int = 100): List<CryptoCoin> {
        val raw = getJson("$COINPAPRIKA_BASE/tickers")
        val arr = JSONArray(raw)
        val list = mutableListOf<CryptoCoin>()
        val count = minOf(arr.length(), limit)
        for (i in 0 until count) {
            val obj = arr.getJSONObject(i)
            val usd = obj.optJSONObject("quotes")?.optJSONObject("USD") ?: continue
            val priceUsd = usd.optDouble("price").takeIf { !it.isNaN() } ?: continue
            list.add(
                CryptoCoin(
                    id = obj.getString("id"),
                    symbol = obj.getString("symbol").uppercase(),
                    name = obj.getString("name"),
                    currentPrice = priceUsd,
                    priceChange24hPct = usd.optDouble("percent_change_24h").takeIf { !it.isNaN() },
                    marketCap = usd.optDouble("market_cap").takeIf { !it.isNaN() },
                    marketCapRank = obj.optInt("rank", 0).takeIf { it > 0 }
                )
            )
        }
        return list
    }

    suspend fun searchCoins(query: String): List<CryptoCoin> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = getJson("$COINPAPRIKA_BASE/search?q=$encoded&c=currencies&limit=35")
        val root = JSONObject(raw)
        val currencies = root.optJSONArray("currencies") ?: JSONArray()
        val list = mutableListOf<CryptoCoin>()
        for (i in 0 until currencies.length()) {
            val obj = currencies.getJSONObject(i)
            list.add(
                CryptoCoin(
                    id = obj.getString("id"),
                    symbol = obj.getString("symbol").uppercase(),
                    name = obj.getString("name"),
                    marketCapRank = obj.optInt("rank", 0).takeIf { it > 0 }
                )
            )
        }
        return list
    }

    suspend fun fetchCoinUsdQuote(coinId: String): QuoteResult {
        val encodedId = URLEncoder.encode(coinId, "UTF-8")
        val raw = getJson("$COINPAPRIKA_BASE/tickers/$encodedId")
        val obj = JSONObject(raw)
        val usd = obj.optJSONObject("quotes")?.optJSONObject("USD")
            ?: throw RuntimeException("No price data available for $coinId")
        return QuoteResult(
            coinId = coinId,
            vsCurrency = "usd",
            unitPrice = usd.getDouble("price"),
            change24hPct = usd.optDouble("percent_change_24h").takeIf { !it.isNaN() },
            marketCap = usd.optDouble("market_cap").takeIf { !it.isNaN() },
            lastUpdatedEpochSec = null
        )
    }
}
