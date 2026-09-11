package dev.gezgin.sample.feature.auth.screen_credentials

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.CredentialsNavigator
import dev.gezgin.sample.navigation.SignUpFlow.CredentialsScreenRoute

@Effects(CredentialsScreenRoute::class)
fun handleCredentialsEffect(
  effect: CredentialsEffect,
  show: (String) -> Unit,
  nav: CredentialsNavigator,
) {
  when (effect) {
    is CredentialsEffect.ShowMessage -> show(effect.text)
    is CredentialsEffect.OpenProfileInfo -> nav.goToProfileInfo(effect.email)
  }
}
