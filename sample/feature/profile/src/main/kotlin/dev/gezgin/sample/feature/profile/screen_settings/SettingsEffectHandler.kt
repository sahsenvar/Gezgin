package dev.gezgin.sample.feature.profile.screen_settings

import android.util.Log
import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.SettingsNavigator

@Effects(SettingsScreenRoute::class)
fun handleSettingsEffect(effect: SettingsEffect, show: (String) -> Unit, nav: SettingsNavigator) {
  when (effect) {
    is SettingsEffect.ShowMessage -> {
      Log.d("SettingsMvi", "effect: ${effect.text}")
      show(effect.text)
    }
    SettingsEffect.Logout -> nav.logout()
  }
}
