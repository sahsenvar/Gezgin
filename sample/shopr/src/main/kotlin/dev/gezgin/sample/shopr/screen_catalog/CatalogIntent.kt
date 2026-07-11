package dev.gezgin.sample.shopr.screen_catalog

sealed interface CatalogIntent {
    data object OpenProduct : CatalogIntent
    data object StartCheckout : CatalogIntent
}
