package dev.gezgin.sample.shopr.screen_payment

sealed interface PaymentEffect {
    data class ShowMessage(val text: String) : PaymentEffect
}
