package dev.gezgin.sample.shopr.screen_order_placed

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

class OrderPlacedViewModel(route: HomeGraph.OrderPlaced) :
  BaseViewModel<OrderPlacedUiState, OrderPlacedIntent, OrderPlacedEffect>() {

  private val _uiState = MutableStateFlow(OrderPlacedUiState(route.orderId))
  override val uiState: StateFlow<OrderPlacedUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<OrderPlacedEffect>()
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

@ViewModelOf(HomeGraph.OrderPlaced::class)
@Composable
fun orderPlacedViewModel(route: HomeGraph.OrderPlaced): OrderPlacedViewModel = viewModel {
  OrderPlacedViewModel(route)
}
