package dev.gezgin.sample.feature.auth.screen_terms

sealed interface TermsIntent {
  data class NameChanged(val value: String) : TermsIntent

  data object BackToStart : TermsIntent

  data object Quit : TermsIntent

  data object Complete : TermsIntent
}
