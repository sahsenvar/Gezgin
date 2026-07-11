package dev.gezgin.sample.shopr.screen_product

import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Product bare route — navigator yok; VM yalnız route verisini (id) tutar.
@MviViewModel(HomeGraph.Product::class)
class ProductViewModel(
    route: HomeGraph.Product,
) : androidx.lifecycle.ViewModel(), GezginMvi<ProductUiState, ProductIntent, ProductEffect> {

    private val _uiState = MutableStateFlow(ProductUiState(id = route.id))
    override val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<ProductEffect>()
    override val effects: Flow<ProductEffect> = _effects.flow

    override fun onIntent(intent: ProductIntent) {
        when (intent) {
            ProductIntent.ToggleFavorite -> {
                _uiState.update { it.copy(favorite = !it.favorite) }
                val text = if (_uiState.value.favorite) "Favorilere eklendi" else "Favorilerden çıkarıldı"
                _effects.send(ProductEffect.Message(text))
            }
        }
    }
}
