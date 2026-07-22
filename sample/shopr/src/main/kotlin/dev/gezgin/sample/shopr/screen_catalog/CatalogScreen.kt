package dev.gezgin.sample.shopr.screen_catalog

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.Catalog
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(Catalog::class)
@Composable
fun CatalogScreen(state: CatalogUiState, onIntent: (CatalogIntent) -> Unit) {
  ScreenChrome(title = "Catalog") {
    Button(onClick = { onIntent(CatalogIntent.OpenProduct) }) {
      Text("Ürüne git (${state.featuredSku})")
    }
    Button(onClick = { onIntent(CatalogIntent.StartCheckout) }) { Text("Checkout başlat") }
  }
}
