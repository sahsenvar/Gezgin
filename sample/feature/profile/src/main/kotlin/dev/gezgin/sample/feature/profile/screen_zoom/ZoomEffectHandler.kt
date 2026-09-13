package dev.gezgin.sample.feature.profile.screen_zoom

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ZoomNavigator

@Effects(ZoomScreenRoute::class)
fun handleZoomEffect(effect: ZoomEffect, show: (String) -> Unit, nav: ZoomNavigator) {
  when (effect) {
    is ZoomEffect.ShowMessage -> show(effect.text)
    is ZoomEffect.Complete -> nav.quitWith(effect.choice)
    ZoomEffect.Back -> nav.back()
  }
}
