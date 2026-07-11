package dev.gezgin.sample.shopr.screen_product

sealed interface ProductEffect {
    data class Message(val text: String) : ProductEffect
}
