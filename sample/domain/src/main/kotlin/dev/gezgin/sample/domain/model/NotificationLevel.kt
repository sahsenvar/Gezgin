package dev.gezgin.sample.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationLevel { ALL, MENTIONS, NONE }
