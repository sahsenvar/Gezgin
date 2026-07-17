package dev.gezgin.sample.feature.profile.screen_profile

import dev.gezgin.core.NavResult
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.domain.model.NotificationLevel

sealed interface ProfileIntent {
    data object EditName : ProfileIntent
    data object OpenSettings : ProfileIntent
    data object PickAvatar : ProfileIntent
    data object PickNotifications : ProfileIntent
    data class AvatarResult(val result: NavResult<AvatarChoice>) : ProfileIntent
    data class EditNameResult(val result: NavResult<String>) : ProfileIntent
    data class NotificationsResult(val result: NavResult<NotificationLevel>) : ProfileIntent
}
