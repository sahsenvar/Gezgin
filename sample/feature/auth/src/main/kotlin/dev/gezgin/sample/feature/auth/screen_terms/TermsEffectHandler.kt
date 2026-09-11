package dev.gezgin.sample.feature.auth.screen_terms

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute
import dev.gezgin.sample.navigation.TermsNavigator

@Effects(TermsScreenRoute::class)
fun handleTermsEffect(effect: TermsEffect, show: (String) -> Unit, nav: TermsNavigator) {
  when (effect) {
    is TermsEffect.ShowMessage -> show(effect.text)
    TermsEffect.BackToStart -> nav.backToStart()
    TermsEffect.Quit -> nav.quit()
    is TermsEffect.Complete -> nav.quitAndGoToWelcome(effect.name)
  }
}
