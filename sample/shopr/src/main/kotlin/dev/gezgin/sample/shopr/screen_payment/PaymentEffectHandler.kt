package dev.gezgin.sample.shopr.screen_payment

import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.PaymentNavigator
import dev.gezgin.sample.shopr.ui.Effects

@Effects(CheckoutFlow.Payment::class)
fun handlePaymentEffect(effect: PaymentEffect, show: (String) -> Unit, nav: PaymentNavigator) {
  when (effect) {
    is PaymentEffect.ShowMessage -> show(effect.text)
    is PaymentEffect.CompletePayment -> nav.quitWith(effect.orderId)
  }
}
