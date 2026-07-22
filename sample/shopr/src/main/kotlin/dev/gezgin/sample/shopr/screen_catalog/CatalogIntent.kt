package dev.gezgin.sample.shopr.screen_catalog

import dev.gezgin.core.NavResult
import dev.gezgin.sample.shopr.nav.OrderId

sealed interface CatalogIntent {
  data object OpenProduct : CatalogIntent

  data object StartCheckout : CatalogIntent

  data class CheckoutResult(val result: NavResult<OrderId>) : CatalogIntent
}
