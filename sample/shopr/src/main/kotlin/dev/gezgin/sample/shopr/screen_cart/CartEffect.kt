package dev.gezgin.sample.shopr.screen_cart

sealed interface CartEffect {
    data class ShowMessage(val text: String) : CartEffect
}
