package dev.gezgin.sample.feature.profile.screen_settings

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

class SettingsViewModel : BaseViewModel<SettingsUiState, SettingsIntent, SettingsEffect>() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  override val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<SettingsEffect>()
  override val effects: Flow<SettingsEffect> = _effects.flow

  override fun onIntent(intent: SettingsIntent) {
    when (intent) {
      SettingsIntent.ToggleTheme -> {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
        _effects.send(SettingsEffect.ShowMessage("Tema tercihi kaydedildi"))
      }
      SettingsIntent.Logout -> _effects.send(SettingsEffect.Logout)
    }
  }
}

@ViewModelOf(ProfileGraph.SettingsScreenRoute::class)
@Composable
fun settingsViewModel(): SettingsViewModel = viewModel { SettingsViewModel() }
