package dev.gezgin.sample.feature.profile.sheet_notification

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.NotificationsSheetNavigator
import dev.gezgin.sample.navigation.ProfileGraph.NotificationsSheetRoute

@Effects(NotificationsSheetRoute::class)
fun handleNotificationsEffect(
  effect: NotificationsEffect,
  show: (String) -> Unit,
  nav: NotificationsSheetNavigator,
) {
  when (effect) {
    is NotificationsEffect.ShowMessage -> show(effect.text)
    is NotificationsEffect.Confirm -> nav.backWithResult(effect.level)
  }
}
