package dev.gezgin.sample.feature.auth.screen_login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gezgin.core.NavResult
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.LoginNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@MviViewModel(LoginScreenRoute::class)
class LoginViewModel(
    private val nav: LoginNavigator,
) : ViewModel(), GezginMvi<LoginUiState, LoginIntent, LoginEffect> {

    private val _uiState = MutableStateFlow(LoginUiState())
    override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<LoginEffect>()
    override val effects: Flow<LoginEffect> = _effects.flow

    // ForgotPassword sonucu init'te Flow olarak toplanır (PD-safe: VM recreate'te — config-change VE gerçek
    // process-death — yeniden subscribe → kalıcı slot'tan teslim). Suspend goToXForResult sugar'ı yalnız
    // process-ömrü içidir (gerçek PD'de düşer); tüm sample sonuç-tüketimi bu re-attach desenini kullanır.
    init {
        viewModelScope.launch {
            nav.forgotPasswordDialogResults.collect { result ->
                val message = when (result) {
                    is NavResult.Value -> if (result.value) "Sıfırlama linki gönderildi" else "Sıfırlama iptal edildi"
                    NavResult.Canceled -> "Sıfırlama iptal edildi"
                }
                _effects.send(LoginEffect.ShowMessage(message))
            }
        }
    }

    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> _uiState.update { it.copy(email = intent.value) }
            is LoginIntent.PasswordChanged -> _uiState.update { it.copy(password = intent.value) }
            LoginIntent.Submit -> nav.loginSuccess()
            LoginIntent.ForgotPassword -> nav.launchForgotPasswordDialog(_uiState.value.email.ifBlank { null })
            LoginIntent.SignUp -> nav.goToSignUp()
        }
    }
}
