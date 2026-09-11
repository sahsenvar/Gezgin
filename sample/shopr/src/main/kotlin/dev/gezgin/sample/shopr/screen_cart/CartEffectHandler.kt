package dev.gezgin.sample.shopr.screen_cart

import dev.gezgin.sample.shopr.nav.CartNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.ui.Effects

@Effects(CheckoutFlow.Cart::class)
fun handleCartEffect(effect: CartEffect, show: (String) -> Unit, nav: CartNavigator) {
  when (effect) {
    is CartEffect.ShowMessage -> show(effect.text)
    CartEffect.NavigateToPayment -> nav.goToPayment()
  }
}
