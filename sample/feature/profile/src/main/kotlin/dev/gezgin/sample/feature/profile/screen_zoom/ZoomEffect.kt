package dev.gezgin.sample.feature.profile.screen_zoom

import dev.gezgin.sample.domain.model.AvatarChoice

sealed interface ZoomEffect {
    data class ShowMessage(val text: String) : ZoomEffect
    data class Complete(val choice: AvatarChoice) : ZoomEffect
    data object Back : ZoomEffect
}
