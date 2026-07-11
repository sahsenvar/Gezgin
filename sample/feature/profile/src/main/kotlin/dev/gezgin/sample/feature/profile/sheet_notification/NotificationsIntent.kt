package dev.gezgin.sample.feature.profile.sheet_notification

import dev.gezgin.sample.navigation.NotificationLevel

sealed interface NotificationsIntent {
    data class Preview(val level: NotificationLevel) : NotificationsIntent
    data object Confirm : NotificationsIntent
}