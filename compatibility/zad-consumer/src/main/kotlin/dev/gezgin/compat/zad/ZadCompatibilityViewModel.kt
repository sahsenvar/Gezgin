package dev.gezgin.compat.zad

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

data class ZadCompatibilityState(val routeName: String)

sealed interface ZadCompatibilityIntent {
    data object Refresh : ZadCompatibilityIntent
}

sealed interface ZadCompatibilityEffect

sealed interface FeaturedCompatibilityEffect

@KoinViewModel
@MviViewModel(ZadCompatibilityRoute::class)
class ZadCompatibilityViewModel(
    @InjectedParam route: ZadCompatibilityRoute,
) : ViewModel(), GezginMvi<ZadCompatibilityState, ZadCompatibilityIntent, ZadCompatibilityEffect> {
    override val uiState: StateFlow<ZadCompatibilityState> = MutableStateFlow(ZadCompatibilityState(route.toString()))

    override fun onIntent(intent: ZadCompatibilityIntent) = Unit
}

@KoinViewModel
@MviViewModel(FeaturedCompatibilityRoute::class)
class FeaturedCompatibilityViewModel(
    @InjectedParam route: FeaturedCompatibilityRoute,
) : ViewModel(), GezginMvi<ZadCompatibilityState, ZadCompatibilityIntent, FeaturedCompatibilityEffect> {
    override val uiState: StateFlow<ZadCompatibilityState> = MutableStateFlow(ZadCompatibilityState(route.toString()))

    override fun onIntent(intent: ZadCompatibilityIntent) = Unit
}
