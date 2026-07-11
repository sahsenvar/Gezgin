package dev.gezgin.sample.shopr.screen_payment

sealed interface PaymentEffect {
    data class Message(val text: String) : PaymentEffect
}
