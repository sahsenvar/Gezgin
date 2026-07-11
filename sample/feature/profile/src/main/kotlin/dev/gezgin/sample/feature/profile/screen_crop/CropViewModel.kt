package dev.gezgin.sample.feature.profile.screen_crop

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.CropNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(CropScreenRoute::class)
class CropViewModel(
    route: CropScreenRoute,
    private val nav: CropNavigator,
) : ViewModel(), GezginMvi<CropUiState, CropIntent, CropEffect> {

    private val _uiState = MutableStateFlow(CropUiState(route.source))
    override val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<CropEffect>()
    override val effects: Flow<CropEffect> = _effects.flow

    override fun onIntent(intent: CropIntent) {
        when (intent) {
            CropIntent.Zoom -> {
                _effects.send(CropEffect.ShowMessage("Yakınlaştırma açıldı"))
                nav.goToZoom()
            }
            CropIntent.Use -> nav.quitWith(AvatarChoice(uri = "avatar://${_uiState.value.source}"))
            CropIntent.Cancel -> nav.back()
        }
    }
}
