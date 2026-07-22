package dev.gezgin.sample.shopr.screen_catalog

import dev.gezgin.sample.shopr.nav.OrderId

sealed interface CatalogEffect {
  data class ShowMessage(val text: String) : CatalogEffect

  data class NavigateToProduct(val productId: String) : CatalogEffect

  data object LaunchCheckout : CatalogEffect

  data class CheckoutCompleted(val orderId: OrderId) : CatalogEffect
}
