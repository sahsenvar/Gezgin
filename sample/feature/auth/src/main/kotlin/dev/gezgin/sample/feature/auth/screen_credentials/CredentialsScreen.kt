package dev.gezgin.sample.feature.auth.screen_credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.SignUpFlow.CredentialsScreenRoute

@Screen(CredentialsScreenRoute::class)
@Composable
fun CredentialsScreen(state: CredentialsUiState, onIntent: (CredentialsIntent) -> Unit) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Kayıt ol — hesap bilgileri")
      OutlinedTextField(
        value = state.email,
        onValueChange = { onIntent(CredentialsIntent.EmailChanged(it)) },
        label = { Text("E-posta") },
        modifier = Modifier.fillMaxWidth(),
      )
      Button(onClick = { onIntent(CredentialsIntent.Continue) }) { Text("Devam") }
    }
  }
}
