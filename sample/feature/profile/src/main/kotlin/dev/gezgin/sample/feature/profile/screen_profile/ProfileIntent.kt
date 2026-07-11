package dev.gezgin.sample.feature.profile.screen_profile

sealed interface ProfileIntent {
    data object EditName : ProfileIntent
    data object OpenSettings : ProfileIntent
    data object PickAvatar : ProfileIntent
    data object PickNotifications : ProfileIntent
}
