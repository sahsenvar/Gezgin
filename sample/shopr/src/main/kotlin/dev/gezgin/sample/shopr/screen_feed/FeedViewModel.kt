package dev.gezgin.sample.shopr.screen_feed

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

class FeedViewModel : BaseViewModel<FeedUiState, FeedIntent, FeedEffect>() {

  private val _uiState = MutableStateFlow(FeedUiState())
  override val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<FeedEffect>()
  override val effects: Flow<FeedEffect> = _effects.flow

  override fun onIntent(intent: FeedIntent) {
    when (intent) {
      FeedIntent.OpenCatalog -> _effects.send(FeedEffect.NavigateToCatalog)
    }
  }
}

@ViewModelOf(HomeGraph.Feed::class)
@Composable
fun feedViewModel(): FeedViewModel = viewModel { FeedViewModel() }
