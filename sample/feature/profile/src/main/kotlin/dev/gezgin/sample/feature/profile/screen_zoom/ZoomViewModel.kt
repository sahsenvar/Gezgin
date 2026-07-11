package dev.gezgin.sample.feature.profile.screen_zoom

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ZoomNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(ZoomScreenRoute::class)
class ZoomViewModel(
    private val nav: ZoomNavigator,
) : ViewModel(), GezginMvi<ZoomUiState, ZoomIntent, ZoomEffect> {

    private val _uiState = MutableStateFlow(ZoomUiState)
    override val uiState: StateFlow<ZoomUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<ZoomEffect>()
    override val effects: Flow<ZoomEffect> = _effects.flow

    override fun onIntent(intent: ZoomIntent) {
        when (intent) {
            ZoomIntent.UseFrame -> {
                _effects.send(ZoomEffect.ShowMessage("Kare seçildi"))
                nav.quitWith(AvatarChoice(uri = "zoomed://frame"))
            }
            ZoomIntent.Back -> nav.back()
        }
    }
}
