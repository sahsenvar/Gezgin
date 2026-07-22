package dev.gezgin.sample.shopr.screen_product

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.Product
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(Product::class)
@Composable
fun ProductScreen(state: ProductUiState, onIntent: (ProductIntent) -> Unit) {
  ScreenChrome(title = "Product ${state.id}") {
    Text("Bare route — navigator'ı yok; VM route verisini state olarak tutar.")
    Text(if (state.favorite) "★ Favoride" else "☆ Favoride değil")
    Button(onClick = { onIntent(ProductIntent.ToggleFavorite) }) { Text("Favori değiştir") }
  }
}
