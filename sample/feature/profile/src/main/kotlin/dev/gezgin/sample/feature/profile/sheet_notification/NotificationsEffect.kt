package dev.gezgin.sample.feature.profile.sheet_notification

sealed interface NotificationsEffect {
    data class Announce(val text: String) : NotificationsEffect
}