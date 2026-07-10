package dev.gezgin.sample.feature.profile

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import dev.gezgin.core.annotation.Screen
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.mvi.annotation.ScreenEffect
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.SettingsNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(val darkTheme: Boolean = false)

data class BuildInfo(val version: String)

sealed interface SettingsIntent {
    data object ToggleTheme : SettingsIntent
    data object Logout : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowMessage(val text: String) : SettingsEffect
}

@MviViewModel(SettingsScreenRoute::class)
class SettingsViewModel(private val nav: SettingsNavigator) :
    ViewModel(),
    GezginMvi<SettingsState, SettingsIntent, SettingsEffect> {

    private val _uiState = MutableStateFlow(SettingsState())
    override val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<SettingsEffect>()
    override val effects: Flow<SettingsEffect> = _effects.flow

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ToggleTheme -> {
                _uiState.update { it.copy(darkTheme = !it.darkTheme) }
                _effects.send(SettingsEffect.ShowMessage("Tema tercihi kaydedildi"))
            }
            SettingsIntent.Logout -> nav.logout()
        }
    }
}

@Screen(SettingsScreenRoute::class)
@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit, buildInfo: BuildInfo) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ayarlar")
            ThemeToggle(state.darkTheme) { onIntent(SettingsIntent.ToggleTheme) }
            Button(onClick = { onIntent(SettingsIntent.Logout) }) { Text("Çıkış yap") }
            Text("Sürüm ${buildInfo.version}")
        }
    }
}

@Composable
private fun ThemeToggle(checked: Boolean, onToggle: () -> Unit) {
    Column {
        Text("Koyu tema")
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@ScreenEffect
@Composable
fun SettingsEffects(effects: Flow<SettingsEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage -> {
                Log.d("SettingsMvi", "effect: ${effect.text}")
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
