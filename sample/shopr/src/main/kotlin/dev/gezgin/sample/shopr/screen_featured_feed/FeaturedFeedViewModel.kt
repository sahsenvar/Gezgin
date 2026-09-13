package dev.gezgin.sample.shopr.screen_featured_feed

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.screen_feed.FeedIntent
import dev.gezgin.sample.shopr.screen_feed.FeedUiState
import dev.gezgin.sample.shopr.ui.BaseViewModel
import dev.gezgin.sample.shopr.ui.EffectSink
import dev.gezgin.sample.shopr.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeaturedFeedViewModel : BaseViewModel<FeedUiState, FeedIntent, FeaturedFeedEffect>() {

  private val _uiState =
    MutableStateFlow(
      FeedUiState(headline = "Haftanın ürünü", primaryActionLabel = "Öne çıkan ürünü aç")
    )
  override val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<FeaturedFeedEffect>()
  override val effects: Flow<FeaturedFeedEffect> = _effects.flow

  override fun onIntent(intent: FeedIntent) {
    when (intent) {
      FeedIntent.OpenCatalog ->
        _effects.send(FeaturedFeedEffect.NavigateToFeaturedProduct(productId = "featured"))
    }
  }
}

@ViewModelOf(HomeGraph.FeaturedFeed::class)
@Composable
fun featuredFeedViewModel(): FeaturedFeedViewModel = viewModel { FeaturedFeedViewModel() }
