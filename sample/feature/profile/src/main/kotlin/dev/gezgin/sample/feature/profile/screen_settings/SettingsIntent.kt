package dev.gezgin.sample.feature.profile.screen_settings

sealed interface SettingsIntent {
    data object ToggleTheme : SettingsIntent
    data object Logout : SettingsIntent
}
