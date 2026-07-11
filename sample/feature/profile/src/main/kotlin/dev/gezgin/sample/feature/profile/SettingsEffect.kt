package dev.gezgin.sample.feature.profile

sealed interface SettingsEffect {
    data class ShowMessage(val text: String) : SettingsEffect
}