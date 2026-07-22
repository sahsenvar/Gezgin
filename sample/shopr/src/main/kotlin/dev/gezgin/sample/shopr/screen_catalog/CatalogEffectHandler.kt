package dev.gezgin.sample.shopr.screen_catalog

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow

@EffectHandler(HomeGraph.Catalog::class)
@Composable
fun CatalogEffectHandler(effects: Flow<CatalogEffect>, nav: CatalogNavigator) {
  val context = LocalContext.current
  val showMessage: (String) -> Unit = { message ->
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }
  val resultIntents = effects.catalogResultIntentSink()

  ObserveEffects(effects) { effect -> handleCatalogEffect(effect, nav, showMessage) }
  LaunchedEffect(effects) {
    nav.checkoutResults.collect { result ->
      resultIntents.send(CatalogIntent.CheckoutResult(result))
    }
  }
}

internal fun handleCatalogEffect(
  effect: CatalogEffect,
  nav: CatalogNavigator,
  onMessage: (String) -> Unit,
) {
  when (effect) {
    is CatalogEffect.ShowMessage -> onMessage(effect.text)
    is CatalogEffect.NavigateToProduct -> nav.goToProduct(effect.productId)
    CatalogEffect.LaunchCheckout -> nav.launchCheckout()
    is CatalogEffect.CheckoutCompleted -> nav.replaceToOrderPlaced(effect.orderId.value)
  }
}
