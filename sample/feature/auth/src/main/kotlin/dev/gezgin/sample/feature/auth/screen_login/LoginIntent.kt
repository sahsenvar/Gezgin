package dev.gezgin.sample.feature.auth.screen_login

sealed interface LoginIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
    data object ForgotPassword : LoginIntent
    data object SignUp : LoginIntent
}
