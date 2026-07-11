package dev.gezgin.sample.feature.home.screen_welcome

sealed interface WelcomeIntent {
    data object OnAppear : WelcomeIntent
    data object Continue : WelcomeIntent
}
