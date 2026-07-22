package dev.gezgin.sample.shopr.screen_order_placed

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.OrderPlacedNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(HomeGraph.OrderPlaced::class)
@Composable
fun OrderPlacedEffectHandler(effects: Flow<OrderPlacedEffect>, nav: OrderPlacedNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    handleOrderPlacedEffect(effect, nav) { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
}

internal fun handleOrderPlacedEffect(
  effect: OrderPlacedEffect,
  nav: OrderPlacedNavigator,
  onMessage: (String) -> Unit,
) {
  when (effect) {
    is OrderPlacedEffect.ShowMessage -> onMessage(effect.text)
    OrderPlacedEffect.BackToFeed -> nav.backToFeed()
    is OrderPlacedEffect.ShowDetails -> nav.showOrderDetails(effect.orderId)
  }
}
