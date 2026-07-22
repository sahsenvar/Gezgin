package dev.gezgin.sample.shopr.screen_order_placed

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.OrderPlaced::class)
class OrderPlacedViewModel(route: HomeGraph.OrderPlaced) :
  ViewModel(), GezginMvi<OrderPlacedUiState, OrderPlacedIntent, OrderPlacedEffect> {

  private val _uiState = MutableStateFlow(OrderPlacedUiState(route.orderId))
  override val uiState: StateFlow<OrderPlacedUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<OrderPlacedEffect>()
  override val effects: Flow<OrderPlacedEffect> = _effects.flow

  init {
    _effects.send(OrderPlacedEffect.ShowMessage("Sipariş ${route.orderId} onaylandı"))
  }

  override fun onIntent(intent: OrderPlacedIntent) {
    when (intent) {
      OrderPlacedIntent.BackToFeed -> _effects.send(OrderPlacedEffect.BackToFeed)
      OrderPlacedIntent.ShowDetails ->
        _effects.send(OrderPlacedEffect.ShowDetails(orderId = _uiState.value.orderId))
    }
  }
}
