package dev.gezgin.sample.shopr.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.OrderPlaced
import dev.gezgin.sample.shopr.nav.OrderPlacedNavigator

@Screen
@Composable
fun OrderPlacedScreen(route: OrderPlaced, nav: OrderPlacedNavigator) {
    ScreenChrome(title = "Order placed: ${route.orderId}") {
        Text("@NoBack terminal ekran — sistem/predictive back kapanır, declared backToFeed() durur.")
        Button(onClick = { nav.backToFeed() }) { Text("Feed'e dön") }
    }
}
