package dev.gezgin.sample.shopr.screen_cart

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(CheckoutFlow.Cart::class)
class CartViewModel : ViewModel(), GezginMvi<CartUiState, CartIntent, CartEffect> {

  private val _uiState = MutableStateFlow(CartUiState())
  override val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<CartEffect>()
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
