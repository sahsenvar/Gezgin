package dev.gezgin.sample.shopr.screen_order_placed

sealed interface OrderPlacedEffect {
    data class Message(val text: String) : OrderPlacedEffect
}
