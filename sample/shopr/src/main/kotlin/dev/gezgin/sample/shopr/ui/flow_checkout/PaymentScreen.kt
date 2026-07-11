package dev.gezgin.sample.shopr.ui.flow_checkout

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Payment
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.PaymentNavigator
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen
@Composable
fun PaymentScreen(route: Payment, nav: PaymentNavigator) {
    ScreenChrome(title = "Payment") {
        Button(onClick = { nav.quitWith(OrderId(value = "ORD-1001")) }) { Text("Ödemeyi tamamla") }
    }
}
