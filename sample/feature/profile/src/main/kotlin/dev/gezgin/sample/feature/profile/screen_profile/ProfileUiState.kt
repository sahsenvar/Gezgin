package dev.gezgin.sample.feature.profile.screen_profile

import dev.gezgin.sample.domain.model.NotificationLevel

data class ProfileUiState(
  val name: String = "Gezgin Kullanıcı",
  val avatarUri: String? = null,
  val notifications: NotificationLevel = NotificationLevel.ALL,
)
