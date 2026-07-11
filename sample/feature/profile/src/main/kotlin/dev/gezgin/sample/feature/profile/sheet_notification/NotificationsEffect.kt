package dev.gezgin.sample.feature.profile.sheet_notification

sealed interface NotificationsEffect {
    data class ShowMessage(val text: String) : NotificationsEffect
}