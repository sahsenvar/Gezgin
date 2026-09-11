package dev.gezgin.sample.feature.auth.screen_login

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.core.NavResult
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoginViewModel : BaseViewModel<LoginUiState, LoginIntent, LoginEffect>() {

  private val _uiState = MutableStateFlow(LoginUiState())
  override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<LoginEffect>()
  override val effects: Flow<LoginEffect> = _effects.flow

  override fun onIntent(intent: LoginIntent) {
    when (intent) {
      is LoginIntent.EmailChanged -> _uiState.update { it.copy(email = intent.value) }
      is LoginIntent.PasswordChanged -> _uiState.update { it.copy(password = intent.value) }
      LoginIntent.Submit -> _effects.send(LoginEffect.LoginSuccess)
      LoginIntent.ForgotPassword ->
        _effects.send(LoginEffect.OpenForgotPassword(_uiState.value.email.ifBlank { null }))
      LoginIntent.SignUp -> _effects.send(LoginEffect.OpenSignUp)
      is LoginIntent.ForgotPasswordResult -> {
        val result = intent.result
        val message =
          when (result) {
            is NavResult.Value ->
              if (result.value) "Sıfırlama linki gönderildi" else "Sıfırlama iptal edildi"
            NavResult.Canceled -> "Sıfırlama iptal edildi"
          }
        _effects.send(LoginEffect.ShowMessage(message))
      }
    }
  }
}

@ViewModelOf(LoginScreenRoute::class)
@Composable
fun loginViewModel(): LoginViewModel = viewModel { LoginViewModel() }
