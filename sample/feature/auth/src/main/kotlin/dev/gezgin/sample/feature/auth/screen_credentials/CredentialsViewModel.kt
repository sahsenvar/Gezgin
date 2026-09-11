package dev.gezgin.sample.feature.auth.screen_credentials

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.SignUpFlow.CredentialsScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CredentialsViewModel :
  BaseViewModel<CredentialsUiState, CredentialsIntent, CredentialsEffect>() {

  private val _uiState = MutableStateFlow(CredentialsUiState())
  override val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<CredentialsEffect>()
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

@ViewModelOf(CredentialsScreenRoute::class)
@Composable
fun credentialsViewModel(): CredentialsViewModel = viewModel { CredentialsViewModel() }
