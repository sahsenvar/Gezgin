package dev.gezgin.sample.feature.profile.screen_profile

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
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute

@Screen(ProfileScreenRoute::class)
@Composable
fun ProfileScreen(state: ProfileUiState, onIntent: (ProfileIntent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profil: ${state.name}")
            Text("Avatar: ${state.avatarUri ?: "(seçilmedi)"}")
            Text("Bildirimler: ${state.notifications}")
            Button(onClick = { onIntent(ProfileIntent.EditName) }) { Text("Adı düzenle") }
            Button(onClick = { onIntent(ProfileIntent.OpenSettings) }) { Text("Ayarlar") }
            Button(onClick = { onIntent(ProfileIntent.PickAvatar) }) { Text("Avatar seç") }
            Button(onClick = { onIntent(ProfileIntent.PickNotifications) }) { Text("Bildirimler") }
        }
    }
}
