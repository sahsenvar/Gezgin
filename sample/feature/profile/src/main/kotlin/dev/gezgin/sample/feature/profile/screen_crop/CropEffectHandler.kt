package dev.gezgin.sample.feature.profile.screen_crop

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.CropNavigator

@Effects(CropScreenRoute::class)
fun handleCropEffect(effect: CropEffect, show: (String) -> Unit, nav: CropNavigator) {
  when (effect) {
    is CropEffect.ShowMessage -> show(effect.text)
    CropEffect.OpenZoom -> nav.goToZoom()
    is CropEffect.Complete -> nav.quitWith(effect.choice)
    CropEffect.Back -> nav.back()
  }
}
