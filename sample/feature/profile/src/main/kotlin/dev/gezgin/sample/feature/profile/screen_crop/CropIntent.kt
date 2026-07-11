package dev.gezgin.sample.feature.profile.screen_crop

sealed interface CropIntent {
    data object Zoom : CropIntent
    data object Use : CropIntent
    data object Cancel : CropIntent
}
