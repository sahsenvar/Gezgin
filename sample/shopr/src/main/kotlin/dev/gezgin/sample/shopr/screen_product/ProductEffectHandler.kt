package dev.gezgin.sample.shopr.screen_product

import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.Effects

@Effects(HomeGraph.Product::class)
fun handleProductEffect(effect: ProductEffect, show: (String) -> Unit) {
  when (effect) {
    is ProductEffect.ShowMessage -> show(effect.text)
  }
}
