package dev.gezgin.sample.shopr.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph.Catalog

@Screen
@Composable
fun CatalogScreen(route: Catalog, nav: CatalogNavigator) {
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
