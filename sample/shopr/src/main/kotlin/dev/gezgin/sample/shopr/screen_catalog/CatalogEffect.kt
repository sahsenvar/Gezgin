package dev.gezgin.sample.shopr.screen_catalog

sealed interface CatalogEffect {
    data class Message(val text: String) : CatalogEffect
}
