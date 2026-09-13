package dev.gezgin.sample.shopr.screen_product

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.BaseViewModel
import dev.gezgin.sample.shopr.ui.EffectSink
import dev.gezgin.sample.shopr.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Product navigation-free VM — strict MVI keeps the typed navigator in the effect handler.
class ProductViewModel(route: HomeGraph.Product) :
  BaseViewModel<ProductUiState, ProductIntent, ProductEffect>() {

  private val _uiState = MutableStateFlow(ProductUiState(id = route.id))
  override val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<ProductEffect>()
  override val effects: Flow<ProductEffect> = _effects.flow

  override fun onIntent(intent: ProductIntent) {
    when (intent) {
      ProductIntent.ToggleFavorite -> {
        _uiState.update { it.copy(favorite = !it.favorite) }
        val text = if (_uiState.value.favorite) "Favorilere eklendi" else "Favorilerden çıkarıldı"
        _effects.send(ProductEffect.ShowMessage(text))
      }
    }
  }
}

@ViewModelOf(HomeGraph.Product::class)
@Composable
fun productViewModel(route: HomeGraph.Product): ProductViewModel = viewModel {
  ProductViewModel(route)
}
