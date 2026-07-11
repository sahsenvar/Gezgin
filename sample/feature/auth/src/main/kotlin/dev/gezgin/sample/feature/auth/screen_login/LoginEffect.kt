package dev.gezgin.sample.feature.auth.screen_login

sealed interface LoginEffect {
    data class ShowMessage(val text: String) : LoginEffect
}
