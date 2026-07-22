package dev.gezgin.sample.shopr.screen_payment

import dev.gezgin.sample.shopr.nav.OrderId

sealed interface PaymentEffect {
  data class ShowMessage(val text: String) : PaymentEffect

  data class CompletePayment(val orderId: OrderId) : PaymentEffect
}
