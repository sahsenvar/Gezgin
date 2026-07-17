package dev.gezgin.sample.feature.profile.screen_crop

import dev.gezgin.sample.domain.model.AvatarChoice

sealed interface CropEffect {
    data class ShowMessage(val text: String) : CropEffect
    data object OpenZoom : CropEffect
    data class Complete(val choice: AvatarChoice) : CropEffect
    data object Back : CropEffect
}
