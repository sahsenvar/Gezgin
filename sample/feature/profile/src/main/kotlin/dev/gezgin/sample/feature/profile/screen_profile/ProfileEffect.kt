package dev.gezgin.sample.feature.profile.screen_profile

import dev.gezgin.sample.domain.model.NotificationLevel

sealed interface ProfileEffect {
  data class ShowMessage(val text: String) : ProfileEffect

  data class EditName(val current: String) : ProfileEffect

  data object OpenSettings : ProfileEffect

  data object PickAvatar : ProfileEffect

  data class PickNotifications(val current: NotificationLevel) : ProfileEffect
}
