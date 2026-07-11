package dev.gezgin.sample.shopr.screen_catalog

sealed interface CatalogEffect {
    data class ShowMessage(val text: String) : CatalogEffect
}
