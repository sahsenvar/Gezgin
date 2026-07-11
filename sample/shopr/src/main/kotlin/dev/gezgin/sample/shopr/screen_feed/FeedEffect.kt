package dev.gezgin.sample.shopr.screen_feed

sealed interface FeedEffect {
    data class ShowMessage(val text: String) : FeedEffect
}
