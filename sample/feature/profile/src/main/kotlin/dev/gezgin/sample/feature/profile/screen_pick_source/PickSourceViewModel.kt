package dev.gezgin.sample.feature.profile.screen_pick_source

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.AvatarFlow.PickSourceScreenRoute
import dev.gezgin.sample.navigation.PickSourceNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(PickSourceScreenRoute::class)
class PickSourceViewModel(
    private val nav: PickSourceNavigator,
) : ViewModel(), GezginMvi<PickSourceUiState, PickSourceIntent, PickSourceEffect> {

    private val _uiState = MutableStateFlow(PickSourceUiState)
    override val uiState: StateFlow<PickSourceUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<PickSourceEffect>()
    override val effects: Flow<PickSourceEffect> = _effects.flow

    override fun onIntent(intent: PickSourceIntent) {
        when (intent) {
            PickSourceIntent.PickGallery -> {
                _effects.send(PickSourceEffect.ShowMessage("Kaynak: Galeri"))
                nav.goToCrop("gallery")
            }
            PickSourceIntent.PickCamera -> {
                _effects.send(PickSourceEffect.ShowMessage("Kaynak: Kamera"))
                nav.goToCrop("camera")
            }
        }
    }
}
