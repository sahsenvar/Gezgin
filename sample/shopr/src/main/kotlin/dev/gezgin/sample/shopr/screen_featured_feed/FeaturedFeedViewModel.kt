package dev.gezgin.sample.shopr.screen_featured_feed

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.screen_feed.FeedIntent
import dev.gezgin.sample.shopr.screen_feed.FeedUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.FeaturedFeed::class)
class FeaturedFeedViewModel : ViewModel(), GezginMvi<FeedUiState, FeedIntent, FeaturedFeedEffect> {

    private val _uiState = MutableStateFlow(
        FeedUiState(
            headline = "Haftanın ürünü",
            primaryActionLabel = "Öne çıkan ürünü aç",
        ),
    )
    override val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<FeaturedFeedEffect>()
    override val effects: Flow<FeaturedFeedEffect> = _effects.flow

    override fun onIntent(intent: FeedIntent) {
        when (intent) {
            FeedIntent.OpenCatalog -> _effects.send(
                FeaturedFeedEffect.NavigateToFeaturedProduct(productId = "featured"),
            )
        }
    }
}
