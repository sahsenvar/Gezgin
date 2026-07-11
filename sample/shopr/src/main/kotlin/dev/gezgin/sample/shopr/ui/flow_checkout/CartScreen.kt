package dev.gezgin.sample.shopr.ui.flow_checkout

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CartNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Cart
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen
@Composable
fun CartScreen(route: Cart, nav: CartNavigator) {
    ScreenChrome(title = "Cart") {
        Button(onClick = { nav.goToPayment() }) { Text("Ödemeye geç") }
    }
}
