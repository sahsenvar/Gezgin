package dev.gezgin.compat.zad

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
@MviViewModel(ZadCompatibilityRoute::class)
class ZadCompatibilityViewModel(
    @InjectedParam route: ZadCompatibilityRoute,
) : ViewModel(), GezginMvi<ZadCompatibilityState, ZadCompatibilityIntent, ZadCompatibilityEffect> {
    override val uiState: StateFlow<ZadCompatibilityState> = MutableStateFlow(ZadCompatibilityState(route.toString()))

    private val _effects = GezginEffects<ZadCompatibilityEffect>()
    override val effects: Flow<ZadCompatibilityEffect> = _effects.flow

    override fun onIntent(intent: ZadCompatibilityIntent) {
        when (intent) {
            ZadCompatibilityIntent.Navigate -> _effects.send(ZadCompatibilityEffect.NavigateToFeatured)
        }
    }
}

@KoinViewModel
@MviViewModel(FeaturedCompatibilityRoute::class)
class FeaturedCompatibilityViewModel(
    @InjectedParam route: FeaturedCompatibilityRoute,
) : ViewModel(), GezginMvi<ZadCompatibilityState, ZadCompatibilityIntent, FeaturedCompatibilityEffect> {
    override val uiState: StateFlow<ZadCompatibilityState> = MutableStateFlow(ZadCompatibilityState(route.toString()))

    private val _effects = GezginEffects<FeaturedCompatibilityEffect>()
    override val effects: Flow<FeaturedCompatibilityEffect> = _effects.flow

    override fun onIntent(intent: ZadCompatibilityIntent) {
        when (intent) {
            ZadCompatibilityIntent.Navigate -> _effects.send(FeaturedCompatibilityEffect.NavigateToHome)
        }
    }
}
