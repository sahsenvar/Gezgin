package dev.gezgin.sample.shopr.screen_order_placed

import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.OrderPlacedNavigator
import dev.gezgin.sample.shopr.ui.Effects

@Effects(HomeGraph.OrderPlaced::class)
fun handleOrderPlacedEffect(
  effect: OrderPlacedEffect,
  show: (String) -> Unit,
  nav: OrderPlacedNavigator,
) {
  when (effect) {
    is OrderPlacedEffect.ShowMessage -> show(effect.text)
    OrderPlacedEffect.BackToFeed -> nav.backToFeed()
    is OrderPlacedEffect.ShowDetails -> nav.showOrderDetails(effect.orderId)
  }
}
