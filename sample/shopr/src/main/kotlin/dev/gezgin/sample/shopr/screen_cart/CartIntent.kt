package dev.gezgin.sample.shopr.screen_cart

sealed interface CartIntent {
    data object Checkout : CartIntent
}
