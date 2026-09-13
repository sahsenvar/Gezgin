package dev.gezgin.sample.shopr.screen_featured_feed

import dev.gezgin.sample.shopr.nav.FeaturedFeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.Effects

@Effects(HomeGraph.FeaturedFeed::class)
fun handleFeaturedFeedEffect(
  effect: FeaturedFeedEffect,
  show: (String) -> Unit,
  nav: FeaturedFeedNavigator,
) {
  when (effect) {
    is FeaturedFeedEffect.NavigateToFeaturedProduct -> nav.goToProduct(effect.productId)
  }
}
