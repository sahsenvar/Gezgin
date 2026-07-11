package dev.gezgin.sample.feature.profile

sealed interface SettingsIntent {
    data object ToggleTheme : SettingsIntent
    data object Logout : SettingsIntent
}