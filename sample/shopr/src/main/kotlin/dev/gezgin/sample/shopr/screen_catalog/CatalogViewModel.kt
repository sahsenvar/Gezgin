package dev.gezgin.sample.shopr.screen_catalog

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.core.NavResult
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.ui.BaseViewModel
import dev.gezgin.sample.shopr.ui.EffectSink
import dev.gezgin.sample.shopr.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CatalogViewModel : BaseViewModel<CatalogUiState, CatalogIntent, CatalogEffect>() {

  private val _uiState = MutableStateFlow(CatalogUiState())
  override val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<CatalogEffect>()
  override val effects: Flow<CatalogEffect> = _effects.flow

  override fun onIntent(intent: CatalogIntent) {
    when (intent) {
      CatalogIntent.OpenProduct ->
        _effects.send(CatalogEffect.NavigateToProduct(productId = _uiState.value.featuredSku))
      CatalogIntent.StartCheckout -> _effects.send(CatalogEffect.LaunchCheckout)
      is CatalogIntent.CheckoutResult ->
        when (val result = intent.result) {
          is NavResult.Value -> _effects.send(CatalogEffect.CheckoutCompleted(result.value))
          NavResult.Canceled -> _effects.send(CatalogEffect.ShowMessage("Ödeme iptal edildi"))
        }
    }
  }
}

@ViewModelOf(HomeGraph.Catalog::class)
@Composable
fun catalogViewModel(): CatalogViewModel = viewModel { CatalogViewModel() }
