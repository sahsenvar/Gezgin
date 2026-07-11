package dev.gezgin.sample.shopr.screen_feed

sealed interface FeedIntent {
    data object OpenCatalog : FeedIntent
}
