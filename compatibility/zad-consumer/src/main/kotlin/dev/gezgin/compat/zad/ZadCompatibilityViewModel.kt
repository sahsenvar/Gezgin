package dev.gezgin.compat.zad

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

data class ZadCompatibilityState(val routeName: String)

sealed interface ZadCompatibilityIntent {
  data object Navigate : ZadCompatibilityIntent
}

sealed interface ZadCompatibilityEffect {
  data object NavigateToFeatured : ZadCompatibilityEffect
}

sealed interface FeaturedCompatibilityEffect {
  data object NavigateToHome : FeaturedCompatibilityEffect
}

@KoinViewModel
class ZadCompatibilityViewModel(@InjectedParam route: ZadCompatibilityRoute) :
  ZadBaseViewModel<ZadCompatibilityState, ZadCompatibilityIntent, ZadCompatibilityEffect>() {
  override val uiState: StateFlow<ZadCompatibilityState> =
    MutableStateFlow(ZadCompatibilityState(route.toString()))

  private val _effects = Channel<ZadCompatibilityEffect>(Channel.UNLIMITED)
  override val effects: Flow<ZadCompatibilityEffect> = _effects.receiveAsFlow()

  override fun onIntent(intent: ZadCompatibilityIntent) {
    when (intent) {
      ZadCompatibilityIntent.Navigate -> _effects.trySend(ZadCompatibilityEffect.NavigateToFeatured)
    }
  }
}

@KoinViewModel
class FeaturedCompatibilityViewModel(@InjectedParam route: FeaturedCompatibilityRoute) :
  ZadBaseViewModel<ZadCompatibilityState, ZadCompatibilityIntent, FeaturedCompatibilityEffect>() {
  override val uiState: StateFlow<ZadCompatibilityState> =
    MutableStateFlow(ZadCompatibilityState(route.toString()))

  private val _effects = Channel<FeaturedCompatibilityEffect>(Channel.UNLIMITED)
  override val effects: Flow<FeaturedCompatibilityEffect> = _effects.receiveAsFlow()

  override fun onIntent(intent: ZadCompatibilityIntent) {
    when (intent) {
      ZadCompatibilityIntent.Navigate ->
        _effects.trySend(FeaturedCompatibilityEffect.NavigateToHome)
    }
  }
}
