package dev.gezgin.sample.shopr.screen_product

sealed interface ProductIntent {
    data object ToggleFavorite : ProductIntent
}
