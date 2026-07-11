package dev.gezgin.sample.shopr.screen_cart

sealed interface CartEffect {
    data class Message(val text: String) : CartEffect
}
