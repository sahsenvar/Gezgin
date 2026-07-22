package dev.gezgin.sample.feature.auth.screen_login

sealed interface LoginEffect {
  data class ShowMessage(val text: String) : LoginEffect

  data object LoginSuccess : LoginEffect

  data class OpenForgotPassword(val email: String?) : LoginEffect

  data object OpenSignUp : LoginEffect
}
