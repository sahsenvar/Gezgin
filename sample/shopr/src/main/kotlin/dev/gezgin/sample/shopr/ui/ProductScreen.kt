package dev.gezgin.sample.shopr.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.Product

@Screen
@Composable
fun ProductScreen(route: Product) {
    ScreenChrome(title = "Product ${route.id}") {
        Text("Bu ekranın navigator'ı yok (bare route) — sadece route verisi gösterilir.")
    }
}
