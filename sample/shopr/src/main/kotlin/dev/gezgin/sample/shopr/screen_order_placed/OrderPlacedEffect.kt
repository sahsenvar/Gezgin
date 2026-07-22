package dev.gezgin.sample.shopr.screen_order_placed

sealed interface OrderPlacedEffect {
  data class ShowMessage(val text: String) : OrderPlacedEffect

  data object BackToFeed : OrderPlacedEffect

  data class ShowDetails(val orderId: String) : OrderPlacedEffect
}
