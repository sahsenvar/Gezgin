package dev.gezgin.sample.feature.profile.screen_profile

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.core.NavResult
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProfileViewModel : BaseViewModel<ProfileUiState, ProfileIntent, ProfileEffect>() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  override val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<ProfileEffect>()
  override val effects: Flow<ProfileEffect> = _effects.flow

  override fun onIntent(intent: ProfileIntent) {
    when (intent) {
      ProfileIntent.EditName -> _effects.send(ProfileEffect.EditName(_uiState.value.name))
      ProfileIntent.OpenSettings -> _effects.send(ProfileEffect.OpenSettings)
      ProfileIntent.PickAvatar -> _effects.send(ProfileEffect.PickAvatar)
      ProfileIntent.PickNotifications ->
        _effects.send(ProfileEffect.PickNotifications(_uiState.value.notifications))
      is ProfileIntent.AvatarResult ->
        if (intent.result is NavResult.Value) {
          _uiState.update { it.copy(avatarUri = intent.result.value.uri) }
          _effects.send(ProfileEffect.ShowMessage("Avatar güncellendi"))
        }
      is ProfileIntent.EditNameResult ->
        if (intent.result is NavResult.Value) {
          _uiState.update { it.copy(name = intent.result.value) }
          _effects.send(ProfileEffect.ShowMessage("Ad güncellendi"))
        }
      is ProfileIntent.NotificationsResult ->
        if (intent.result is NavResult.Value) {
          _uiState.update { it.copy(notifications = intent.result.value) }
          _effects.send(ProfileEffect.ShowMessage("Bildirim düzeyi güncellendi"))
        }
    }
  }
}

@ViewModelOf(ProfileScreenRoute::class)
@Composable
fun profileViewModel(): ProfileViewModel = viewModel { ProfileViewModel() }
