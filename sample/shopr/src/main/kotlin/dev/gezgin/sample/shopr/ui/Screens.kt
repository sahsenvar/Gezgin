package dev.gezgin.sample.shopr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CartNavigator
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Cart
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Payment
import dev.gezgin.sample.shopr.nav.HomeGraph.Catalog
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.HomeGraph.OrderPlaced
import dev.gezgin.sample.shopr.nav.HomeGraph.Product
import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.OrderPlacedNavigator
import dev.gezgin.sample.shopr.nav.PaymentNavigator

/**
 * Task 3.6 — bir `@Screen` composable'ı per route, sade Text+Button chrome (görsel değil, canlı-derleme
 * kanıtı için). Codegen'in `provideXEntry`'si (EntryCodegen) bu dosyanın `@Screen` fonksiyonlarını okuyup
 * aynı pakette `GezginEntries.kt` üretir (docs/gezgin-by-example.md §2/§4 core-mode deseni).
 */
@Screen
@Composable
fun FeedScreen(route: Feed, nav: FeedNavigator) {
    ScreenChrome(title = "Feed") {
        Button(onClick = { nav.goToCatalog() }) { Text("Kataloğa git") }
    }
}

@Screen
@Composable
fun CatalogScreen(route: Catalog, nav: CatalogNavigator) {
    // Core-mode result-collection (ViewModel'siz, docs/gezgin-by-example.md §4): launch tetiği ve
    // re-attach edilebilir Flow ayrı ayrı üretilir — CheckoutFlow'un sonucu (RealOrderId) burada
    // PD-safe biçimde toplanır ve alınınca replaceToOrderPlaced ile terminal ekrana geçilir.
    LaunchedEffect(Unit) {
        nav.checkoutResults.collect { result ->
            when (result) {
                is NavResult.Value -> nav.replaceToOrderPlaced(result.value.value)
                NavResult.Canceled -> Unit
            }
        }
    }
    ScreenChrome(title = "Catalog") {
        Button(onClick = { nav.goToProduct(id = "sku-42") }) { Text("Ürüne git") }
        Button(onClick = { nav.launchCheckout() }) { Text("Checkout başlat") }
    }
}

@Screen
@Composable
fun ProductScreen(route: Product) {
    ScreenChrome(title = "Product ${route.id}") {
        Text("Bu ekranın navigator'ı yok (bare route) — sadece route verisi gösterilir.")
    }
}

@Screen
@Composable
fun OrderPlacedScreen(route: OrderPlaced, nav: OrderPlacedNavigator) {
    ScreenChrome(title = "Order placed: ${route.orderId}") {
        Text("@NoBack terminal ekran — sistem/predictive back kapanır, declared backToFeed() durur.")
        Button(onClick = { nav.backToFeed() }) { Text("Feed'e dön") }
    }
}

@Screen
@Composable
fun CartScreen(route: Cart, nav: CartNavigator) {
    ScreenChrome(title = "Cart") {
        Button(onClick = { nav.goToPayment() }) { Text("Ödemeye geç") }
    }
}

@Screen
@Composable
fun PaymentScreen(route: Payment, nav: PaymentNavigator) {
    ScreenChrome(title = "Payment") {
        Button(onClick = { nav.quitWith(OrderId(value = "ORD-1001")) }) { Text("Ödemeyi tamamla") }
    }
}

@Composable
private fun ScreenChrome(title: String, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title)
            content()
        }
    }
}
