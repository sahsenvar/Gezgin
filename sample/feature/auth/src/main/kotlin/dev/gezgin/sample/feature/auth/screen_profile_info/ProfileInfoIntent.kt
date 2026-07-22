package dev.gezgin.sample.feature.auth.screen_profile_info

sealed interface ProfileInfoIntent {
  data object Continue : ProfileInfoIntent
}
