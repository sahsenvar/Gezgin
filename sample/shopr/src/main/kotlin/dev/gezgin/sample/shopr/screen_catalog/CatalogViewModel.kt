package dev.gezgin.sample.shopr.screen_catalog

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.Catalog::class)
class CatalogViewModel : ViewModel(), GezginMvi<CatalogUiState, CatalogIntent, CatalogEffect> {

    private val _uiState = MutableStateFlow(CatalogUiState())
    override val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<CatalogEffect>()
    override val effects: Flow<CatalogEffect> = _effects.flow

    override fun onIntent(intent: CatalogIntent) {
        when (intent) {
            CatalogIntent.OpenProduct -> _effects.send(
                CatalogEffect.NavigateToProduct(productId = _uiState.value.featuredSku),
            )
            CatalogIntent.StartCheckout -> _effects.send(CatalogEffect.LaunchCheckout)
        }
    }
}
