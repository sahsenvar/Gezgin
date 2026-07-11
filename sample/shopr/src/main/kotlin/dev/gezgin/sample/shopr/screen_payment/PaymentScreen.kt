package dev.gezgin.sample.shopr.screen_payment

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.CheckoutFlow.Payment
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(Payment::class)
@Composable
fun PaymentScreen(state: PaymentUiState, onIntent: (PaymentIntent) -> Unit) {
    ScreenChrome(title = "Payment ${state.amount}") {
        Button(onClick = { onIntent(PaymentIntent.Pay) }) { Text("Ödemeyi tamamla") }
    }
}
