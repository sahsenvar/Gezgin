package dev.gezgin.compat.zad

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

data class ZadCompatibilityState(val route: ZadCompatibilityRoute)

sealed interface ZadCompatibilityIntent {
    data object Refresh : ZadCompatibilityIntent
}

sealed interface ZadCompatibilityEffect

@KoinViewModel
@MviViewModel(ZadCompatibilityRoute::class)
class ZadCompatibilityViewModel(
    @InjectedParam route: ZadCompatibilityRoute,
) : ViewModel(), GezginMvi<ZadCompatibilityState, ZadCompatibilityIntent, ZadCompatibilityEffect> {
    override val uiState: StateFlow<ZadCompatibilityState> = MutableStateFlow(ZadCompatibilityState(route))

    override fun onIntent(intent: ZadCompatibilityIntent) = Unit
}
