package dev.gezgin.sample.feature.auth.screen_profile_info

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
import dev.gezgin.sample.navigation.SignUpFlow.ProfileInfoScreenRoute

@Screen(ProfileInfoScreenRoute::class)
@Composable
fun ProfileInfoScreen(state: ProfileInfoUiState, onIntent: (ProfileInfoIntent) -> Unit) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Kayıt ol — profil bilgileri")
      Text("E-posta: ${state.email}")
      Button(onClick = { onIntent(ProfileInfoIntent.Continue) }) { Text("Devam") }
    }
  }
}
