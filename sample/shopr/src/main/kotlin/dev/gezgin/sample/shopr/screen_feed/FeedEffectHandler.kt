package dev.gezgin.sample.shopr.screen_feed

import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.Effects

@Effects(HomeGraph.Feed::class)
fun handleFeedEffect(effect: FeedEffect, show: (String) -> Unit, nav: FeedNavigator) {
  when (effect) {
    FeedEffect.NavigateToCatalog -> nav.goToCatalog()
  }
}
