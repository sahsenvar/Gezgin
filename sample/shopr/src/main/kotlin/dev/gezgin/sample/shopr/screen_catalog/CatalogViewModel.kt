package dev.gezgin.sample.shopr.screen_catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@MviViewModel(HomeGraph.Catalog::class)
class CatalogViewModel(
    private val nav: CatalogNavigator,
) : ViewModel(), GezginMvi<CatalogUiState, CatalogIntent, CatalogEffect> {

    private val _uiState = MutableStateFlow(CatalogUiState())
    override val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<CatalogEffect>()
    override val effects: Flow<CatalogEffect> = _effects.flow

    init {
        // @GoForResult(CheckoutFlow) sonucu VM'de toplanır: Value → @ReplaceTo(OrderPlaced), iptal → effect.
        viewModelScope.launch {
            nav.checkoutResults.collect { result ->
                when (result) {
                    is NavResult.Value -> nav.replaceToOrderPlaced(result.value.value)
                    NavResult.Canceled -> _effects.send(CatalogEffect.ShowMessage("Ödeme iptal edildi"))
                }
            }
        }
    }

    override fun onIntent(intent: CatalogIntent) {
        when (intent) {
            CatalogIntent.OpenProduct -> nav.goToProduct(id = _uiState.value.featuredSku)
            CatalogIntent.StartCheckout -> nav.launchCheckout()
        }
    }
}
