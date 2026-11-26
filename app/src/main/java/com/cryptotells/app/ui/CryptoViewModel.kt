package com.cryptotells.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotells.app.data.CryptoCoin
import com.cryptotells.app.data.CryptoRepository
import com.cryptotells.app.data.FiatCurrency
import com.cryptotells.app.data.QuoteResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CryptoUiState(
    val amountInput: String = "100",
    val isCurrencyToCrypto: Boolean = true,
    val selectedCurrency: FiatCurrency = FiatCurrency("usd", "US Dollar"),
    val selectedCoin: CryptoCoin = CryptoCoin("btc-bitcoin", "BTC", "Bitcoin"),
    val currencies: List<FiatCurrency> = CryptoRepository.POPULAR_CURRENCIES.map { FiatCurrency(it.key, it.value) },
    val usdRates: Map<String, Double> = mapOf("usd" to 1.0),
    val topCoinsUsd: List<CryptoCoin> = emptyList(),
    val topCoins: List<CryptoCoin> = emptyList(),
    val searchResults: List<CryptoCoin> = emptyList(),
    val coinSearchQuery: String = "",
    val currencySearchQuery: String = "",
    val selectedCoinUsdPrice: Double? = null,
    val selectedCoinChange24h: Double? = null,
    val selectedCoinMarketCap: Double? = null,
    val quote: QuoteResult? = null,
    val isLoading: Boolean = false,
    val isSearchingCoins: Boolean = false,
    val errorMessage: String? = null
)

class CryptoViewModel(
    private val repo: CryptoRepository = CryptoRepository()
) : ViewModel() {

    var uiState by mutableStateOf(CryptoUiState())
        private set

    private var searchJob: Job? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                val (currencies, rates) = repo.fetchUsdRates()
                val topCoinsUsd = repo.fetchTopCoinsUsd(100)
                val defaultCoin = topCoinsUsd.firstOrNull { it.id == uiState.selectedCoin.id }
                    ?: topCoinsUsd.firstOrNull()
                    ?: uiState.selectedCoin

                uiState = uiState.copy(
                    currencies = currencies,
                    usdRates = rates,
                    topCoinsUsd = topCoinsUsd,
                    selectedCoin = defaultCoin,
                    selectedCoinUsdPrice = defaultCoin.currentPrice,
                    selectedCoinChange24h = defaultCoin.priceChange24hPct,
                    selectedCoinMarketCap = defaultCoin.marketCap,
                    isLoading = false
                )
                recomputeConvertedRates()
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unable to fetch live market data"
                )
            }
        }
    }

    private fun recomputeConvertedRates() {
        val fxRate = uiState.usdRates[uiState.selectedCurrency.code.lowercase()] ?: 1.0
        val convertedTop = uiState.topCoinsUsd.map { coin ->
            coin.copy(currentPrice = coin.currentPrice?.times(fxRate))
        }
        val usdPrice = uiState.selectedCoinUsdPrice
        val quote = if (usdPrice != null) {
            QuoteResult(
                coinId = uiState.selectedCoin.id,
                vsCurrency = uiState.selectedCurrency.code,
                unitPrice = usdPrice * fxRate,
                change24hPct = uiState.selectedCoinChange24h,
                marketCap = uiState.selectedCoinMarketCap?.times(fxRate),
                lastUpdatedEpochSec = null
            )
        } else null

        uiState = uiState.copy(
            topCoins = convertedTop,
            quote = quote
        )
    }

    fun onAmountChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } <= 1) {
            uiState = uiState.copy(amountInput = cleaned)
        }
    }

    fun toggleDirection() {
        uiState = uiState.copy(isCurrencyToCrypto = !uiState.isCurrencyToCrypto)
    }

    fun selectCurrency(currency: FiatCurrency) {
        uiState = uiState.copy(
            selectedCurrency = currency,
            currencySearchQuery = "",
            errorMessage = null
        )
        recomputeConvertedRates()
    }

    fun selectCoin(coin: CryptoCoin) {
        val cached = uiState.topCoinsUsd.firstOrNull { it.id == coin.id }
        if (cached?.currentPrice != null) {
            uiState = uiState.copy(
                selectedCoin = cached,
                coinSearchQuery = "",
                searchResults = emptyList(),
                selectedCoinUsdPrice = cached.currentPrice,
                selectedCoinChange24h = cached.priceChange24hPct,
                selectedCoinMarketCap = cached.marketCap,
                errorMessage = null
            )
            recomputeConvertedRates()
            return
        }

        uiState = uiState.copy(
            selectedCoin = coin,
            coinSearchQuery = "",
            searchResults = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                val usdQuote = repo.fetchCoinUsdQuote(coin.id)
                uiState = uiState.copy(
                    selectedCoinUsdPrice = usdQuote.unitPrice,
                    selectedCoinChange24h = usdQuote.change24hPct,
                    selectedCoinMarketCap = usdQuote.marketCap,
                    isLoading = false
                )
                recomputeConvertedRates()
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load ${coin.symbol}"
                )
            }
        }
    }

    fun onCurrencySearchChange(query: String) {
        uiState = uiState.copy(currencySearchQuery = query)
    }

    fun onCoinSearchChange(query: String) {
        uiState = uiState.copy(coinSearchQuery = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            uiState = uiState.copy(searchResults = emptyList(), isSearchingCoins = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            uiState = uiState.copy(isSearchingCoins = true)
            try {
                val results = repo.searchCoins(query)
                uiState = uiState.copy(searchResults = results, isSearchingCoins = false)
            } catch (_: Exception) {
                uiState = uiState.copy(isSearchingCoins = false)
            }
        }
    }

    fun refreshQuote() {
        loadInitialData()
    }
}
