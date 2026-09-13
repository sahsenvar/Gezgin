package dev.gezgin.sample.feature.profile.sheet_notification

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.ProfileGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NotificationsViewModel(route: ProfileGraph.NotificationsSheetRoute) :
  BaseViewModel<NotificationsUiState, NotificationsIntent, NotificationsEffect>() {

  private val _uiState = MutableStateFlow(NotificationsUiState(route.current))
  override val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<NotificationsEffect>()
  override val effects: Flow<NotificationsEffect> = _effects.flow

  override fun onIntent(intent: NotificationsIntent) {
    when (intent) {
      is NotificationsIntent.Preview -> {
        _uiState.update { it.copy(selected = intent.level) }
        _effects.send(NotificationsEffect.ShowMessage("Önizleme: ${intent.level}"))
      }
      NotificationsIntent.Confirm ->
        _effects.send(NotificationsEffect.Confirm(_uiState.value.selected))
    }
  }
}

@ViewModelOf(ProfileGraph.NotificationsSheetRoute::class)
@Composable
fun notificationsViewModel(route: ProfileGraph.NotificationsSheetRoute): NotificationsViewModel =
  viewModel {
    NotificationsViewModel(route)
  }
