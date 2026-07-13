package dev.gezgin.sample.shopr.screen_order_placed

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.OrderPlaced
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(OrderPlaced::class)
@Composable
fun OrderPlacedScreen(state: OrderPlacedUiState, onIntent: (OrderPlacedIntent) -> Unit) {
    ScreenChrome(title = "Order placed: ${state.orderId}") {
        Text("@NoBack terminal ekran — sistem/predictive back kapanır, declared backToFeed() durur.")
        Button(onClick = { onIntent(OrderPlacedIntent.ShowDetails) }) { Text("Detayları göster") }
        Button(onClick = { onIntent(OrderPlacedIntent.BackToFeed) }) { Text("Feed'e dön") }
    }
}
