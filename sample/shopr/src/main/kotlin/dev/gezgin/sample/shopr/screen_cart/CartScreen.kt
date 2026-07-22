package dev.gezgin.sample.shopr.screen_cart

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Cart
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(Cart::class)
@Composable
fun CartScreen(state: CartUiState, onIntent: (CartIntent) -> Unit) {
  ScreenChrome(title = "Cart (${state.itemCount})") {
    Button(onClick = { onIntent(CartIntent.Checkout) }) { Text("Ödemeye geç") }
  }
}
