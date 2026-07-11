package dev.gezgin.sample.feature.auth.screen_credentials

sealed interface CredentialsIntent {
    data class EmailChanged(val value: String) : CredentialsIntent
    data object Continue : CredentialsIntent
}
