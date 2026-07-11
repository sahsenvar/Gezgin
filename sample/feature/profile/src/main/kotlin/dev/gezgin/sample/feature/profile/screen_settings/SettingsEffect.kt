package dev.gezgin.sample.feature.profile.screen_settings

sealed interface SettingsEffect {
    data class ShowMessage(val text: String) : SettingsEffect
}
