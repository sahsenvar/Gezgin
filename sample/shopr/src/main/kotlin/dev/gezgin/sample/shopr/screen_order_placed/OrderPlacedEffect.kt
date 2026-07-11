package dev.gezgin.sample.shopr.screen_order_placed

sealed interface OrderPlacedEffect {
    data class ShowMessage(val text: String) : OrderPlacedEffect
}
