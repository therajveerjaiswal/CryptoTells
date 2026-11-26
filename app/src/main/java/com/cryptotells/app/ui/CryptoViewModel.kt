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
    val selectedCoin: CryptoCoin = CryptoCoin("bitcoin", "BTC", "Bitcoin"),
    val currencies: List<FiatCurrency> = CryptoRepository.POPULAR_CURRENCIES.map { FiatCurrency(it.key, it.value) },
    val topCoins: List<CryptoCoin> = emptyList(),
    val searchResults: List<CryptoCoin> = emptyList(),
    val coinSearchQuery: String = "",
    val currencySearchQuery: String = "",
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
                val currencies = repo.fetchSupportedCurrencies()
                val topCoins = repo.fetchTopCoins(uiState.selectedCurrency.code)
                val defaultCoin = topCoins.firstOrNull { it.id == uiState.selectedCoin.id }
                    ?: topCoins.firstOrNull()
                    ?: uiState.selectedCoin
                val quote = repo.fetchQuote(defaultCoin.id, uiState.selectedCurrency.code)
                uiState = uiState.copy(
                    currencies = currencies,
                    topCoins = topCoins,
                    selectedCoin = defaultCoin,
                    quote = quote,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unable to fetch live market data"
                )
            }
        }
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
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                val quote = repo.fetchQuote(uiState.selectedCoin.id, currency.code)
                val topCoins = repo.fetchTopCoins(currency.code)
                uiState = uiState.copy(
                    quote = quote,
                    topCoins = topCoins,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load rate for ${currency.code.uppercase()}"
                )
            }
        }
    }

    fun selectCoin(coin: CryptoCoin) {
        uiState = uiState.copy(
            selectedCoin = coin,
            coinSearchQuery = "",
            searchResults = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                val quote = repo.fetchQuote(coin.id, uiState.selectedCurrency.code)
                uiState = uiState.copy(quote = quote, isLoading = false)
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
        selectCoin(uiState.selectedCoin)
    }
}
