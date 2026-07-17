package dev.gezgin.sample.feature.profile.sheet_notification

import dev.gezgin.sample.domain.model.NotificationLevel

sealed interface NotificationsEffect {
    data class ShowMessage(val text: String) : NotificationsEffect
    data class Confirm(val level: NotificationLevel) : NotificationsEffect
}
