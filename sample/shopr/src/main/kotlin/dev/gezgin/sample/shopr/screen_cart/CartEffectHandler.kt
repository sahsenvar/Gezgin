package dev.gezgin.sample.shopr.screen_cart

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.CartNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import kotlinx.coroutines.flow.Flow

@EffectHandler(CheckoutFlow.Cart::class)
@Composable
fun CartEffectHandler(effects: Flow<CartEffect>, nav: CartNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    handleCartEffect(effect, nav) { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
}

internal fun handleCartEffect(effect: CartEffect, nav: CartNavigator, onMessage: (String) -> Unit) {
  when (effect) {
    is CartEffect.ShowMessage -> onMessage(effect.text)
    CartEffect.NavigateToPayment -> nav.goToPayment()
  }
}
