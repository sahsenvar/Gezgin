package dev.gezgin.sample.feature.profile.notification

sealed interface NotificationsEffect {
    data class Announce(val text: String) : NotificationsEffect
}