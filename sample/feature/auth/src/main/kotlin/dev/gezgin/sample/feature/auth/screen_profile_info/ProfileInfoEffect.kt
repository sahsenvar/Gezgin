package dev.gezgin.sample.feature.auth.screen_profile_info

sealed interface ProfileInfoEffect {
  data class ShowMessage(val text: String) : ProfileInfoEffect

  data object OpenTerms : ProfileInfoEffect
}
