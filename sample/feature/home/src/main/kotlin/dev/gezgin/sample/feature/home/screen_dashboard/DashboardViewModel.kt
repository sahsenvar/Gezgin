package dev.gezgin.sample.feature.home.screen_dashboard

import androidx.lifecycle.ViewModel
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.feature.home.resultIntentEffectFlow
import dev.gezgin.sample.navigation.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(HomeGraph.DashboardScreenRoute::class)
class DashboardViewModel : ViewModel(), GezginMvi<DashboardUiState, DashboardIntent, DashboardEffect> {

    private val _uiState = MutableStateFlow(DashboardUiState())
    override val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<DashboardEffect>()
    override val effects: Flow<DashboardEffect> = resultIntentEffectFlow(_effects.flow, ::onIntent)

    override fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.OpenItem -> _effects.send(DashboardEffect.OpenItem(intent.id))
            DashboardIntent.OpenProfile -> _effects.send(DashboardEffect.OpenProfile)
            DashboardIntent.OpenHelp -> _effects.send(DashboardEffect.OpenHelp(topic = "navigasyon"))
            DashboardIntent.PickSort -> _effects.send(DashboardEffect.OpenSortPicker(_uiState.value.order.name))
            is DashboardIntent.SortResult -> {
                val result = intent.result
                if (result is NavResult.Value) {
                    _uiState.update { it.copy(order = result.value) }
                    _effects.send(DashboardEffect.ShowMessage("Sıralama: ${result.value}"))
                }
            }
        }
    }
}
