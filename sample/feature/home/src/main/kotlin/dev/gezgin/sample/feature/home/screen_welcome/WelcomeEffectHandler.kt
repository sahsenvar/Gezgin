package dev.gezgin.sample.feature.home.screen_welcome

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.HomeGraph.WelcomeScreenRoute
import dev.gezgin.sample.navigation.WelcomeNavigator

@Effects(WelcomeScreenRoute::class)
fun handleWelcomeEffect(effect: WelcomeEffect, show: (String) -> Unit, nav: WelcomeNavigator) {
  when (effect) {
    is WelcomeEffect.ShowMessage -> show(effect.text)
    WelcomeEffect.ContinueToDashboard -> nav.continueToDashboard()
  }
}
