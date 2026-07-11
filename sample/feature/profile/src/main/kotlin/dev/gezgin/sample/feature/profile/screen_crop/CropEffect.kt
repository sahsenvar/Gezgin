package dev.gezgin.sample.feature.profile.screen_crop

sealed interface CropEffect {
    data class ShowMessage(val text: String) : CropEffect
}
