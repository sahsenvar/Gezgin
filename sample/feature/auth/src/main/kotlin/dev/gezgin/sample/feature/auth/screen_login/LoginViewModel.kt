package dev.gezgin.sample.feature.auth.screen_login

import androidx.lifecycle.ViewModel
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.feature.auth.resultIntentEffectFlow
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(LoginScreenRoute::class)
class LoginViewModel : ViewModel(), GezginMvi<LoginUiState, LoginIntent, LoginEffect> {

    private val _uiState = MutableStateFlow(LoginUiState())
    override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<LoginEffect>()
    override val effects: Flow<LoginEffect> = resultIntentEffectFlow(_effects.flow, ::onIntent)

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
                val message = when (result) {
                    is NavResult.Value -> if (result.value) "Sıfırlama linki gönderildi" else "Sıfırlama iptal edildi"
                    NavResult.Canceled -> "Sıfırlama iptal edildi"
                }
                _effects.send(LoginEffect.ShowMessage(message))
            }
        }
    }
}
