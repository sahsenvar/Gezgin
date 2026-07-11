package dev.gezgin.sample.feature.auth.screen_terms

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute
import dev.gezgin.sample.navigation.TermsNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(TermsScreenRoute::class)
class TermsViewModel(
    private val nav: TermsNavigator,
) : ViewModel(), GezginMvi<TermsUiState, TermsIntent, TermsEffect> {

    private val _uiState = MutableStateFlow(TermsUiState())
    override val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<TermsEffect>()
    override val effects: Flow<TermsEffect> = _effects.flow

    override fun onIntent(intent: TermsIntent) {
        when (intent) {
            is TermsIntent.NameChanged -> _uiState.update { it.copy(name = intent.value) }
            TermsIntent.BackToStart -> nav.backToStart()
            TermsIntent.Quit -> nav.quit()
            TermsIntent.Complete -> {
                _effects.send(TermsEffect.ShowMessage("Kayıt tamamlandı"))
                nav.quitAndGoToWelcome(_uiState.value.name.ifBlank { null })
            }
        }
    }
}
