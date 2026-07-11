package dev.gezgin.sample.shopr.screen_payment

sealed interface PaymentIntent {
    data object Pay : PaymentIntent
}
