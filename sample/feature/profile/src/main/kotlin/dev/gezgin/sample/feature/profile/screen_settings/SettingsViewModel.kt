package dev.gezgin.sample.feature.profile.screen_settings

import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.ProfileGraph
import dev.gezgin.sample.navigation.SettingsNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(ProfileGraph.SettingsScreenRoute::class)
class SettingsViewModel(
    private val nav: SettingsNavigator
) : androidx.lifecycle.ViewModel(), GezginMvi<SettingsUiState, SettingsIntent, SettingsEffect> {

    private val _uiState = MutableStateFlow(SettingsUiState())
    override val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<SettingsEffect>()
    override val effects: Flow<SettingsEffect> = _effects.flow

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ToggleTheme -> {
                _uiState.update { it.copy(darkTheme = !it.darkTheme) }
                _effects.send(SettingsEffect.ShowMessage("Tema tercihi kaydedildi"))
            }
            SettingsIntent.Logout -> nav.logout()
        }
    }
}
