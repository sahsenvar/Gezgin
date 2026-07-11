package dev.gezgin.sample.feature.home.screen_item_detail

sealed interface ItemDetailEffect {
    data class ShowMessage(val text: String) : ItemDetailEffect
}
