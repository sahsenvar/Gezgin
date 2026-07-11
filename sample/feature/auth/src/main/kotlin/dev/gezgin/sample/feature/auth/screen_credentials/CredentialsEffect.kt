package dev.gezgin.sample.feature.auth.screen_credentials

sealed interface CredentialsEffect {
    data class ShowMessage(val text: String) : CredentialsEffect
}
