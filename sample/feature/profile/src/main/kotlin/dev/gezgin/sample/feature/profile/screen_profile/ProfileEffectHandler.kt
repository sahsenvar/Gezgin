package dev.gezgin.sample.feature.profile.screen_profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.designsystem.ResultCollector
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator

@Effects(ProfileScreenRoute::class)
fun handleProfileEffect(effect: ProfileEffect, show: (String) -> Unit, nav: ProfileNavigator) {
  when (effect) {
    is ProfileEffect.ShowMessage -> show(effect.text)
    is ProfileEffect.EditName -> nav.launchEditNameDialog(effect.current)
    ProfileEffect.OpenSettings -> nav.goToSettings()
    ProfileEffect.PickAvatar -> nav.launchPickAvatar()
    is ProfileEffect.PickNotifications -> nav.launchPickNotifications(effect.current)
  }
}

/** Three PD-safe result streams, all re-entering the ViewModel through the same slot. */
@ResultCollector(ProfileScreenRoute::class)
@Composable
fun ProfileResultCollector(onIntent: (ProfileIntent) -> Unit, nav: ProfileNavigator) {
  LaunchedEffect(nav) {
    nav.pickAvatarResults.collect { result -> onIntent(ProfileIntent.AvatarResult(result)) }
  }
  LaunchedEffect(nav) {
    nav.editNameDialogResults.collect { result -> onIntent(ProfileIntent.EditNameResult(result)) }
  }
  LaunchedEffect(nav) {
    nav.pickNotificationsResults.collect { result ->
      onIntent(ProfileIntent.NotificationsResult(result))
    }
  }
}
