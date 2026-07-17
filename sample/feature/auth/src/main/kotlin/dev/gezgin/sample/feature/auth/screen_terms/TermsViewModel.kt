package dev.gezgin.sample.feature.auth.screen_terms

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(TermsScreenRoute::class)
class TermsViewModel : ViewModel(), GezginMvi<TermsUiState, TermsIntent, TermsEffect> {

    private val _uiState = MutableStateFlow(TermsUiState())
    override val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<TermsEffect>()
    override val effects: Flow<TermsEffect> = _effects.flow

    override fun onIntent(intent: TermsIntent) {
        when (intent) {
            is TermsIntent.NameChanged -> _uiState.update { it.copy(name = intent.value) }
            TermsIntent.BackToStart -> _effects.send(TermsEffect.BackToStart)
            TermsIntent.Quit -> _effects.send(TermsEffect.Quit)
            TermsIntent.Complete ->
                if (_uiState.value.name.isBlank()) {
                    _effects.send(TermsEffect.ShowMessage("Devam etmek için adınızı girin"))
                } else {
                    _effects.send(TermsEffect.Complete(_uiState.value.name))
                }
        }
    }
}
