package dev.gezgin.sample.feature.profile.notification

import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.ViewModel
import dev.gezgin.sample.navigation.NotificationsSheetNavigator
import dev.gezgin.sample.navigation.ProfileGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@ViewModel(ProfileGraph.NotificationsSheetRoute::class)
class NotificationsViewModel(
    route: ProfileGraph.NotificationsSheetRoute,
    private val nav: NotificationsSheetNavigator,
) : androidx.lifecycle.ViewModel(), GezginMvi<NotificationsUiState, NotificationsIntent, NotificationsEffect> {

    private val _uiState = MutableStateFlow(NotificationsUiState(route.current))
    override val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<NotificationsEffect>()
    override val effects: Flow<NotificationsEffect> = _effects.flow

    override fun onIntent(intent: NotificationsIntent) {
        when (intent) {
            is NotificationsIntent.Preview -> {
                _uiState.update { it.copy(selected = intent.level) }
                _effects.send(NotificationsEffect.Announce("Önizleme: ${intent.level}"))
            }
            NotificationsIntent.Confirm -> nav.backWithResult(_uiState.value.selected)
        }
    }
}