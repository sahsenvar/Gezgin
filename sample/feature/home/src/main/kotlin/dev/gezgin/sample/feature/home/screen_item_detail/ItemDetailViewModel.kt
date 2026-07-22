package dev.gezgin.sample.feature.home.screen_item_detail

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(HomeGraph.ItemDetailScreenRoute::class)
class ItemDetailViewModel(route: HomeGraph.ItemDetailScreenRoute) :
  ViewModel(), GezginMvi<ItemDetailUiState, ItemDetailIntent, ItemDetailEffect> {

  private val _uiState = MutableStateFlow(ItemDetailUiState(id = route.id))
  override val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<ItemDetailEffect>()
  override val effects: Flow<ItemDetailEffect> = _effects.flow

  override fun onIntent(intent: ItemDetailIntent) {
    when (intent) {
      ItemDetailIntent.OnAppear -> {
        _uiState.update { it.copy(visits = it.visits + 1) }
        _effects.send(ItemDetailEffect.ShowMessage("${_uiState.value.visits}. görüntüleme"))
      }
      ItemDetailIntent.OpenRelated -> _effects.send(ItemDetailEffect.OpenRelated(_uiState.value.id))
      ItemDetailIntent.OpenImage -> _effects.send(ItemDetailEffect.OpenImage(_uiState.value.id))
      ItemDetailIntent.Back -> _effects.send(ItemDetailEffect.BackToDashboard)
    }
  }
}
