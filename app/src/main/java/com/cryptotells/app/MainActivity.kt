package com.cryptotells.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cryptotells.app.data.CryptoCoin
import com.cryptotells.app.data.FiatCurrency
import com.cryptotells.app.ui.CryptoViewModel
import java.text.DecimalFormat
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CryptoTellsTheme {
                CryptoTellsScreen()
            }
        }
    }
}

@Composable
fun CryptoTellsTheme(content: @Composable () -> Unit) {
    val darkScheme = darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF062032),
        secondary = Color(0xFF22C55E),
        background = Color(0xFF0B1120),
        surface = Color(0xFF131C31),
        surfaceVariant = Color(0xFF1E293B),
        onBackground = Color(0xFFF1F5F9),
        onSurface = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF94A3B8),
        error = Color(0xFFF87171)
    )
    MaterialTheme(colorScheme = darkScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoTellsScreen(vm: CryptoViewModel = viewModel()) {
    val state = vm.uiState
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showCoinPicker by remember { mutableStateOf(false) }

    val amount = state.amountInput.toDoubleOrNull() ?: 0.0
    val unitPrice = state.quote?.unitPrice ?: 0.0
    val convertedValue = remember(amount, unitPrice, state.isCurrencyToCrypto) {
        if (unitPrice <= 0.0) 0.0
        else if (state.isCurrencyToCrypto) amount / unitPrice
        else amount * unitPrice
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CryptoTells", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Any Currency ↔ Any Crypto",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshQuote() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh rates")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B181E)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = state.errorMessage,
                            color = Color(0xFFFCA5A5),
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (state.isCurrencyToCrypto) "You Pay (Currency)" else "You Pay (Crypto)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isCurrencyToCrypto) showCurrencyPicker = true
                                    else showCoinPicker = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (state.isCurrencyToCrypto)
                                        state.selectedCurrency.code.uppercase()
                                    else state.selectedCoin.symbol,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = state.amountInput,
                                onValueChange = vm::onAmountChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("0.00") }
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FilledTonalIconButton(onClick = vm::toggleDirection) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Swap direction")
                            }
                        }

                        Text(
                            text = if (state.isCurrencyToCrypto) "You Get (Crypto)" else "You Get (Currency)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isCurrencyToCrypto) showCoinPicker = true
                                    else showCurrencyPicker = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (state.isCurrencyToCrypto)
                                        state.selectedCoin.symbol
                                    else state.selectedCurrency.code.uppercase(),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = formatNumber(convertedValue),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (state.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        state.quote?.let { q ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1 ${state.selectedCoin.symbol} = ${formatNumber(q.unitPrice)} ${q.vsCurrency.uppercase()}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                q.change24hPct?.let { pct ->
                                    val isUp = pct >= 0
                                    Text(
                                        text = "${if (isUp) "+" else ""}${String.format("%.2f", pct)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isUp) Color(0xFF4ADE80) else Color(0xFFF87171)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Compare ${state.amountInput.ifBlank { "0" }} ${state.selectedCurrency.code.uppercase()} across top coins",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(state.topCoins.take(20), key = { it.id }) { coin ->
                val price = coin.currentPrice ?: 0.0
                val equivalent = if (price > 0) amount / price else 0.0
                val change = coin.priceChange24hPct
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectCoin(coin) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${coin.name} (${coin.symbol})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "1 ${coin.symbol} = ${formatNumber(price)} ${state.selectedCurrency.code.uppercase()}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${formatNumber(equivalent)} ${coin.symbol}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (change != null) {
                                Text(
                                    text = "${if (change >= 0) "+" else ""}${String.format("%.2f", change)}%",
                                    fontSize = 12.sp,
                                    color = if (change >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currencies = state.currencies,
            query = state.currencySearchQuery,
            onQueryChange = vm::onCurrencySearchChange,
            onSelect = {
                vm.selectCurrency(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    if (showCoinPicker) {
        CoinPickerDialog(
            topCoins = state.topCoins,
            searchResults = state.searchResults,
            query = state.coinSearchQuery,
            isSearching = state.isSearchingCoins,
            onQueryChange = vm::onCoinSearchChange,
            onSelect = {
                vm.selectCoin(it)
                showCoinPicker = false
            },
            onDismiss = { showCoinPicker = false }
        )
    }
}

@Composable
fun CurrencyPickerDialog(
    currencies: List<FiatCurrency>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (FiatCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(currencies, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) currencies
        else currencies.filter { it.code.contains(q) || it.name.lowercase().contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search USD, INR, EUR...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.code }) { c ->
                        ListItem(
                            headlineContent = { Text(c.code.uppercase(), fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(c.name) },
                            modifier = Modifier.clickable { onSelect(c) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun CoinPickerDialog(
    topCoins: List<CryptoCoin>,
    searchResults: List<CryptoCoin>,
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (CryptoCoin) -> Unit,
    onDismiss: () -> Unit
) {
    val list = if (query.trim().length >= 2 && searchResults.isNotEmpty()) searchResults else topCoins
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Crypto Coin or Token") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search BTC, SOL, PEPE...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(list, key = { it.id }) { coin ->
                        ListItem(
                            headlineContent = { Text("${coin.name} (${coin.symbol})", fontWeight = FontWeight.SemiBold) },
                            supportingContent = coin.marketCapRank?.let { { Text("Rank #$it") } },
                            modifier = Modifier.clickable { onSelect(coin) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatNumber(value: Double): String {
    val v = abs(value)
    return when {
        value == 0.0 -> "0"
        v >= 1000.0 -> DecimalFormat("#,##0.00").format(value)
        v >= 1.0 -> DecimalFormat("#,##0.####").format(value)
        v >= 0.0001 -> DecimalFormat("0.######").format(value)
        else -> DecimalFormat("0.##########").format(value)
    }
}
