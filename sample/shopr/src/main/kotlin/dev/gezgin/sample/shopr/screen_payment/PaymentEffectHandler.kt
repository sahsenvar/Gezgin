package dev.gezgin.sample.shopr.screen_payment

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.PaymentNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(CheckoutFlow.Payment::class)
@Composable
fun PaymentEffectHandler(effects: Flow<PaymentEffect>, nav: PaymentNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    handlePaymentEffect(effect, nav) { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
}

internal fun handlePaymentEffect(
  effect: PaymentEffect,
  nav: PaymentNavigator,
  onMessage: (String) -> Unit,
) {
  when (effect) {
    is PaymentEffect.ShowMessage -> onMessage(effect.text)
    is PaymentEffect.CompletePayment -> nav.quitWith(effect.orderId)
  }
}
