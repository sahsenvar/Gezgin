package dev.gezgin.sample.feature.profile.screen_pick_source

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.AvatarFlow.PickSourceScreenRoute
import dev.gezgin.sample.navigation.PickSourceNavigator

@Effects(PickSourceScreenRoute::class)
fun handlePickSourceEffect(
  effect: PickSourceEffect,
  show: (String) -> Unit,
  nav: PickSourceNavigator,
) {
  when (effect) {
    is PickSourceEffect.ShowMessage -> show(effect.text)
    is PickSourceEffect.OpenCrop -> nav.goToCrop(effect.source)
  }
}
