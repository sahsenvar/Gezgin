package dev.gezgin.sample.feature.home.screen_welcome

import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.HomeGraph
import dev.gezgin.sample.navigation.WelcomeNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HomeGraph.WelcomeScreenRoute::class)
class WelcomeViewModel(
    route: HomeGraph.WelcomeScreenRoute,
    private val nav: WelcomeNavigator,
) : androidx.lifecycle.ViewModel(), GezginMvi<WelcomeUiState, WelcomeIntent, WelcomeEffect> {

    private val _uiState = MutableStateFlow(WelcomeUiState(name = route.name))
    override val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<WelcomeEffect>()
    override val effects: Flow<WelcomeEffect> = _effects.flow

    override fun onIntent(intent: WelcomeIntent) {
        when (intent) {
            // Efekt Continue'da DEĞİL OnAppear'da: @ReplaceTo Welcome entry'sini kaldırır → Continue'da
            // gönderilen efekti hiçbir observer toplayamadan ekran yok olur (kayıp toast).
            WelcomeIntent.OnAppear ->
                _effects.send(WelcomeEffect.ShowMessage(_uiState.value.name?.let { "Merhaba $it" } ?: "Merhaba"))
            WelcomeIntent.Continue -> nav.continueToDashboard()
        }
    }
}
