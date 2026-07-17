package dev.gezgin.sample.shopr.screen_feed

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.Feed::class)
class FeedViewModel : ViewModel(), GezginMvi<FeedUiState, FeedIntent, FeedEffect> {

    private val _uiState = MutableStateFlow(FeedUiState())
    override val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<FeedEffect>()
    override val effects: Flow<FeedEffect> = _effects.flow

    override fun onIntent(intent: FeedIntent) {
        when (intent) {
            FeedIntent.OpenCatalog -> _effects.send(FeedEffect.NavigateToCatalog)
        }
    }
}
