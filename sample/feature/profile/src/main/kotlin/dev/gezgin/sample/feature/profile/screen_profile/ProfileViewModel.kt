package dev.gezgin.sample.feature.profile.screen_profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@MviViewModel(ProfileScreenRoute::class)
class ProfileViewModel(
    private val nav: ProfileNavigator,
) : ViewModel(), GezginMvi<ProfileUiState, ProfileIntent, ProfileEffect> {

    private val _uiState = MutableStateFlow(ProfileUiState())
    override val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<ProfileEffect>()
    override val effects: Flow<ProfileEffect> = _effects.flow

    init {
        viewModelScope.launch {
            nav.pickAvatarResults.collect { result ->
                if (result is NavResult.Value) {
                    _uiState.update { it.copy(avatarUri = result.value.uri) }
                    _effects.send(ProfileEffect.ShowMessage("Avatar güncellendi"))
                }
            }
        }
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.EditName -> viewModelScope.launch {
                val result = nav.goToEditNameDialogForResult(_uiState.value.name)
                if (result is NavResult.Value) {
                    _uiState.update { it.copy(name = result.value) }
                    _effects.send(ProfileEffect.ShowMessage("Ad güncellendi"))
                }
            }
            ProfileIntent.OpenSettings -> nav.goToSettings()
            ProfileIntent.PickAvatar -> nav.launchPickAvatar()
            ProfileIntent.PickNotifications -> viewModelScope.launch {
                val result = nav.goToPickNotificationsForResult(_uiState.value.notifications)
                if (result is NavResult.Value) {
                    _uiState.update { it.copy(notifications = result.value) }
                    _effects.send(ProfileEffect.ShowMessage("Bildirim düzeyi güncellendi"))
                }
            }
        }
    }
}
