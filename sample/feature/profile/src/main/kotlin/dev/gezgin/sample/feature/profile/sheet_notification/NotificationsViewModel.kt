package dev.gezgin.sample.feature.profile.sheet_notification

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.ProfileGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@MviViewModel(ProfileGraph.NotificationsSheetRoute::class)
class NotificationsViewModel(
    route: ProfileGraph.NotificationsSheetRoute,
) : ViewModel(), GezginMvi<NotificationsUiState, NotificationsIntent, NotificationsEffect> {

    private val _uiState = MutableStateFlow(NotificationsUiState(route.current))
    override val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<NotificationsEffect>()
    override val effects: Flow<NotificationsEffect> = _effects.flow

    override fun onIntent(intent: NotificationsIntent) {
        when (intent) {
            is NotificationsIntent.Preview -> {
                _uiState.update { it.copy(selected = intent.level) }
                _effects.send(NotificationsEffect.ShowMessage("Önizleme: ${intent.level}"))
            }
            NotificationsIntent.Confirm -> _effects.send(NotificationsEffect.Confirm(_uiState.value.selected))
        }
    }
}
