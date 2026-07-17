package dev.gezgin.sample.feature.profile.screen_profile

import androidx.lifecycle.ViewModel
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.feature.profile.resultIntentEffectFlow
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(ProfileScreenRoute::class)
class ProfileViewModel : ViewModel(), GezginMvi<ProfileUiState, ProfileIntent, ProfileEffect> {

    private val _uiState = MutableStateFlow(ProfileUiState())
    override val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<ProfileEffect>()
    override val effects: Flow<ProfileEffect> = resultIntentEffectFlow(_effects.flow, ::onIntent)

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.EditName -> _effects.send(ProfileEffect.EditName(_uiState.value.name))
            ProfileIntent.OpenSettings -> _effects.send(ProfileEffect.OpenSettings)
            ProfileIntent.PickAvatar -> _effects.send(ProfileEffect.PickAvatar)
            ProfileIntent.PickNotifications ->
                _effects.send(ProfileEffect.PickNotifications(_uiState.value.notifications))
            is ProfileIntent.AvatarResult -> if (intent.result is NavResult.Value) {
                _uiState.update { it.copy(avatarUri = intent.result.value.uri) }
                _effects.send(ProfileEffect.ShowMessage("Avatar güncellendi"))
            }
            is ProfileIntent.EditNameResult -> if (intent.result is NavResult.Value) {
                _uiState.update { it.copy(name = intent.result.value) }
                _effects.send(ProfileEffect.ShowMessage("Ad güncellendi"))
            }
            is ProfileIntent.NotificationsResult -> if (intent.result is NavResult.Value) {
                _uiState.update { it.copy(notifications = intent.result.value) }
                _effects.send(ProfileEffect.ShowMessage("Bildirim düzeyi güncellendi"))
            }
        }
    }
}
