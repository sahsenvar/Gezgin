package dev.gezgin.sample.feature.profile.screen_zoom

sealed interface ZoomIntent {
    data object UseFrame : ZoomIntent
    data object Back : ZoomIntent
}
