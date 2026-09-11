package dev.gezgin.sample.feature.auth.screen_profile_info

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.ProfileInfoNavigator
import dev.gezgin.sample.navigation.SignUpFlow.ProfileInfoScreenRoute

@Effects(ProfileInfoScreenRoute::class)
fun handleProfileInfoEffect(
  effect: ProfileInfoEffect,
  show: (String) -> Unit,
  nav: ProfileInfoNavigator,
) {
  when (effect) {
    is ProfileInfoEffect.ShowMessage -> show(effect.text)
    ProfileInfoEffect.OpenTerms -> nav.goToTerms()
  }
}
