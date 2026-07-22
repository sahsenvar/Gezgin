package dev.gezgin.sample.shopr.screen_featured_feed

sealed interface FeaturedFeedEffect {
  data class NavigateToFeaturedProduct(val productId: String) : FeaturedFeedEffect
}
