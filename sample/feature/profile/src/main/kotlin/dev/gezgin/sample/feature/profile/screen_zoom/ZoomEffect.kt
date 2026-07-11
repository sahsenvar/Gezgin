package dev.gezgin.sample.feature.profile.screen_zoom

sealed interface ZoomEffect {
    data class ShowMessage(val text: String) : ZoomEffect
}
