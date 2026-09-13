package dev.gezgin.sample.feature.home.screen_dashboard

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.core.NavResult
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DashboardViewModel : BaseViewModel<DashboardUiState, DashboardIntent, DashboardEffect>() {

  private val _uiState = MutableStateFlow(DashboardUiState())
  override val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<DashboardEffect>()
  override val effects: Flow<DashboardEffect> = _effects.flow

  override fun onIntent(intent: DashboardIntent) {
    when (intent) {
      is DashboardIntent.OpenItem -> _effects.send(DashboardEffect.OpenItem(intent.id))
      DashboardIntent.OpenProfile -> _effects.send(DashboardEffect.OpenProfile)
      DashboardIntent.OpenHelp -> _effects.send(DashboardEffect.OpenHelp(topic = "navigasyon"))
      DashboardIntent.PickSort ->
        _effects.send(DashboardEffect.OpenSortPicker(_uiState.value.order.name))
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

@ViewModelOf(HomeGraph.DashboardScreenRoute::class)
@Composable
fun dashboardViewModel(): DashboardViewModel = viewModel { DashboardViewModel() }
