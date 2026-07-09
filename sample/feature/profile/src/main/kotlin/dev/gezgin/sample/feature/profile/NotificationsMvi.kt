@file:OptIn(ExperimentalMaterial3Api::class)

package dev.gezgin.sample.feature.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel as AndroidxViewModel
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.ObserveAsEvents
import dev.gezgin.mvi.annotation.ScreenEffect
import dev.gezgin.mvi.annotation.ViewModel
import dev.gezgin.sample.navigation.NotificationLevel
import dev.gezgin.sample.navigation.NotificationsSheetNavigator
import dev.gezgin.sample.navigation.ProfileGraph.NotificationsSheetRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI-mode `@BottomSheet` (Integ M3) — the only sample route combining an MVI triple with a modal kind.
 * Drives the full kind'd chain live: `register(kind=BOTTOM_SHEET)` → sheet scene → generated
 * `viewModel(nav, route)` → `LocalGezginSheetState` role param (MV8). Loss-free effects via [GezginEffects].
 */

data class NotificationsState(val selected: NotificationLevel)

sealed interface NotificationsIntent {
    data class Preview(val level: NotificationLevel) : NotificationsIntent
    data object Confirm : NotificationsIntent
}

sealed interface NotificationsEffect {
    data class Announce(val text: String) : NotificationsEffect
}

@ViewModel(NotificationsSheetRoute::class)
class NotificationsViewModel(
    route: NotificationsSheetRoute,
    private val nav: NotificationsSheetNavigator,
) : AndroidxViewModel(), GezginMvi<NotificationsState, NotificationsIntent, NotificationsEffect> {

    private val _uiState = MutableStateFlow(NotificationsState(route.current))
    override val uiState: StateFlow<NotificationsState> = _uiState.asStateFlow()

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

/** `sheetState` = MV8 role param (`LocalGezginSheetState.current`) — hide-then-pop for a clean close. */
@BottomSheet(NotificationsSheetRoute::class)
@Composable
fun NotificationsSheetContent(
    state: NotificationsState,
    onIntent: (NotificationsIntent) -> Unit,
    sheetState: SheetState,
) {
    val scope = rememberCoroutineScope()
    var dispatched by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bildirim düzeyi — seçili: ${state.selected}")
        NotificationLevel.entries.forEach { level ->
            Button(
                onClick = { onIntent(NotificationsIntent.Preview(level)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(level.name) }
        }
        Button(
            onClick = onClick@{
                if (dispatched) return@onClick
                dispatched = true
                scope.launch {
                    sheetState.hide()
                    onIntent(NotificationsIntent.Confirm)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kaydet") }
    }
}

@ScreenEffect
@Composable
fun NotificationsEffects(effects: Flow<NotificationsEffect>) {
    val context = LocalContext.current
    ObserveAsEvents(effects) { effect ->
        when (effect) {
            is NotificationsEffect.Announce -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
