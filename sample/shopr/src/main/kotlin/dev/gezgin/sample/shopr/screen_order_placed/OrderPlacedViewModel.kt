package dev.gezgin.sample.shopr.screen_order_placed

import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.OrderPlacedNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.OrderPlaced::class)
class OrderPlacedViewModel(
    route: HomeGraph.OrderPlaced,
    private val nav: OrderPlacedNavigator,
) : androidx.lifecycle.ViewModel(), GezginMvi<OrderPlacedUiState, OrderPlacedIntent, OrderPlacedEffect> {

    private val _uiState = MutableStateFlow(OrderPlacedUiState(route.orderId))
    override val uiState: StateFlow<OrderPlacedUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<OrderPlacedEffect>()
    override val effects: Flow<OrderPlacedEffect> = _effects.flow

    init {
        _effects.send(OrderPlacedEffect.Message("Sipariş ${route.orderId} onaylandı"))
    }

    override fun onIntent(intent: OrderPlacedIntent) {
        when (intent) {
            // @NoBack terminal ekran; deklare edilen @BackTo(Feed) navigator üzerinden yürür.
            OrderPlacedIntent.BackToFeed -> nav.backToFeed()
        }
    }
}
