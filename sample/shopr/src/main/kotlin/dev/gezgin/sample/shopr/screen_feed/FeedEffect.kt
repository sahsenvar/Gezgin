package dev.gezgin.sample.shopr.screen_feed

sealed interface FeedEffect {
  data object NavigateToCatalog : FeedEffect
}
