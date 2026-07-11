package dev.gezgin.sample.feature.home.screen_welcome

sealed interface WelcomeEffect {
    data class ShowMessage(val text: String) : WelcomeEffect
}
