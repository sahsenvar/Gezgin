package dev.gezgin.sample.shopr.screen_feed

sealed interface FeedEffect {
    data class Message(val text: String) : FeedEffect
}
