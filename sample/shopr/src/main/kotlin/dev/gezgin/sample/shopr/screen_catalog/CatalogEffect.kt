package dev.gezgin.sample.shopr.screen_catalog

sealed interface CatalogEffect {
    data class ShowMessage(val text: String) : CatalogEffect
    data class NavigateToProduct(val productId: String) : CatalogEffect
    data object LaunchCheckout : CatalogEffect
}
