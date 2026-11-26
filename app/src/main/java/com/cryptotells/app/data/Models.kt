package com.cryptotells.app.data

data class FiatCurrency(
    val code: String,
    val name: String
)

data class CryptoCoin(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double? = null,
    val priceChange24hPct: Double? = null,
    val marketCap: Double? = null,
    val marketCapRank: Int? = null
)

data class QuoteResult(
    val coinId: String,
    val vsCurrency: String,
    val unitPrice: Double,
    val change24hPct: Double?,
    val marketCap: Double?,
    val lastUpdatedEpochSec: Long?
)
