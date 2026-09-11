package dev.gezgin.sample.shopr.screen_catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.Effects
import dev.gezgin.sample.shopr.ui.ResultCollector

@Effects(HomeGraph.Catalog::class)
fun handleCatalogEffect(effect: CatalogEffect, show: (String) -> Unit, nav: CatalogNavigator) {
  when (effect) {
    is CatalogEffect.ShowMessage -> show(effect.text)
    is CatalogEffect.NavigateToProduct -> nav.goToProduct(effect.productId)
    CatalogEffect.LaunchCheckout -> nav.launchCheckout()
    is CatalogEffect.CheckoutCompleted -> nav.replaceToOrderPlaced(effect.orderId.value)
  }
}

/**
 * The PD-safe result stream must be collected inside composition, which a plain effect provider is
 * not — so it fills its own slot and receives `onIntent` from the wrapper.
 */
@ResultCollector(HomeGraph.Catalog::class)
@Composable
fun CatalogResultCollector(onIntent: (CatalogIntent) -> Unit, nav: CatalogNavigator) {
  LaunchedEffect(nav) {
    nav.checkoutResults.collect { result -> onIntent(CatalogIntent.CheckoutResult(result)) }
  }
}
