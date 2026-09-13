package dev.gezgin.sample.feature.auth.screen_profile_info

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.SignUpFlow.ProfileInfoScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileInfoViewModel(route: ProfileInfoScreenRoute) :
  BaseViewModel<ProfileInfoUiState, ProfileInfoIntent, ProfileInfoEffect>() {

  private val _uiState = MutableStateFlow(ProfileInfoUiState(route.email))
  override val uiState: StateFlow<ProfileInfoUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<ProfileInfoEffect>()
  override val effects: Flow<ProfileInfoEffect> = _effects.flow

  // Giriş ipucu entry yaratılırken gönderilir (nav'dan önce DEĞİL); lossless kanal STARTED'da
  // toplar.
  init {
    _effects.send(ProfileInfoEffect.ShowMessage("Hesap: ${_uiState.value.email}"))
  }

  override fun onIntent(intent: ProfileInfoIntent) {
    when (intent) {
      ProfileInfoIntent.Continue -> _effects.send(ProfileInfoEffect.OpenTerms)
    }
  }
}

@ViewModelOf(ProfileInfoScreenRoute::class)
@Composable
fun profileInfoViewModel(route: ProfileInfoScreenRoute): ProfileInfoViewModel = viewModel {
  ProfileInfoViewModel(route)
}
