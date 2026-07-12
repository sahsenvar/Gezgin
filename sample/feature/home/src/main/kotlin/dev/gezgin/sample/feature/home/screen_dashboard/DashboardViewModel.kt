package dev.gezgin.sample.feature.home.screen_dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@MviViewModel(HomeGraph.DashboardScreenRoute::class)
class DashboardViewModel(
    private val nav: DashboardNavigator,
) : ViewModel(), GezginMvi<DashboardUiState, DashboardIntent, DashboardEffect> {

    private val _uiState = MutableStateFlow(DashboardUiState())
    override val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<DashboardEffect>()
    override val effects: Flow<DashboardEffect> = _effects.flow

    // pickSort @GoForResult sonucu init'te Flow olarak toplanır → VM recreate'te (config-change VE gerçek
    // process-death) yeniden subscribe → kalıcı slot'tan teslim = PD-safe. Cihazda am-kill ile doğrulandı
    // (bkz. maestro/run-18b-sheet-pd.sh). Suspend goToPickSortForResult sugar'ı yalnız process-ömrü içidir.
    init {
        viewModelScope.launch {
            nav.pickSortResults.collect { result ->
                if (result is NavResult.Value) {
                    _uiState.update { it.copy(order = result.value) }
                    _effects.send(DashboardEffect.ShowMessage("Sıralama: ${result.value}"))
                }
            }
        }
    }

    override fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.OpenItem -> nav.goToItemDetail(intent.id)
            DashboardIntent.OpenProfile -> nav.goToProfile()
            DashboardIntent.OpenHelp -> nav.goToHelp(topic = "navigasyon")
            DashboardIntent.PickSort -> nav.launchPickSort(_uiState.value.order.name)
        }
    }
}
