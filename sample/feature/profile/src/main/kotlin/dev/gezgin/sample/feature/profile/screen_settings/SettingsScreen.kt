package dev.gezgin.sample.feature.profile.screen_settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.feature.profile.screen_settings.ui.ThemeToggle
import dev.gezgin.sample.navigation.ProfileGraph

@Screen(ProfileGraph.SettingsScreenRoute::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, buildInfo: BuildInfo) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ayarlar")
            ThemeToggle(state.darkTheme) { onIntent(SettingsIntent.ToggleTheme) }
            Button(onClick = { onIntent(SettingsIntent.Logout) }) { Text("Çıkış yap") }
            Text("Sürüm ${buildInfo.version}")
        }
    }
}
