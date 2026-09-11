package dev.gezgin.sample.shopr.screen_cart

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.ui.BaseViewModel
import dev.gezgin.sample.shopr.ui.EffectSink
import dev.gezgin.sample.shopr.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CartViewModel : BaseViewModel<CartUiState, CartIntent, CartEffect>() {

  private val _uiState = MutableStateFlow(CartUiState())
  override val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<CartEffect>()
  override val effects: Flow<CartEffect> = _effects.flow

  init {
    _effects.send(CartEffect.ShowMessage("Sepetinizde ${_uiState.value.itemCount} ürün var"))
  }

  override fun onIntent(intent: CartIntent) {
    when (intent) {
      CartIntent.Checkout -> _effects.send(CartEffect.NavigateToPayment)
    }
  }
}

@ViewModelOf(CheckoutFlow.Cart::class)
@Composable
fun cartViewModel(): CartViewModel = viewModel { CartViewModel() }
