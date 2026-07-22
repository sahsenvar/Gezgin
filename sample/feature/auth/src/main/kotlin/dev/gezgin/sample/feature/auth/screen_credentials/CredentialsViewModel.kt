package dev.gezgin.sample.feature.auth.screen_credentials

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.SignUpFlow.CredentialsScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(CredentialsScreenRoute::class)
class CredentialsViewModel :
  ViewModel(), GezginMvi<CredentialsUiState, CredentialsIntent, CredentialsEffect> {

  private val _uiState = MutableStateFlow(CredentialsUiState())
  override val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<CredentialsEffect>()
  override val effects: Flow<CredentialsEffect> = _effects.flow

  override fun onIntent(intent: CredentialsIntent) {
    when (intent) {
      is CredentialsIntent.EmailChanged -> _uiState.update { it.copy(email = intent.value) }
      CredentialsIntent.Continue ->
        if (_uiState.value.email.isBlank()) {
          _effects.send(CredentialsEffect.ShowMessage("Devam etmek için e-posta girin"))
        } else {
          _effects.send(CredentialsEffect.OpenProfileInfo(_uiState.value.email))
        }
    }
  }
}
