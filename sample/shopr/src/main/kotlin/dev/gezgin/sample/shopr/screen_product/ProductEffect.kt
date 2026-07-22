package dev.gezgin.sample.shopr.screen_product

sealed interface ProductEffect {
  data class ShowMessage(val text: String) : ProductEffect
}
