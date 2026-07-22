package dev.gezgin.sample.feature.auth.screen_terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute

@Screen(TermsScreenRoute::class)
@Composable
fun TermsScreen(state: TermsUiState, onIntent: (TermsIntent) -> Unit) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Kayıt ol — kullanım koşulları")
      OutlinedTextField(
        value = state.name,
        onValueChange = { onIntent(TermsIntent.NameChanged(it)) },
        label = { Text("Adınız (Welcome ekranı için)") },
        modifier = Modifier.fillMaxWidth(),
      )
      Button(onClick = { onIntent(TermsIntent.BackToStart) }) { Text("Başa dön") }
      TextButton(onClick = { onIntent(TermsIntent.Quit) }) { Text("Vazgeç") }
      Button(onClick = { onIntent(TermsIntent.Complete) }) { Text("Kaydı tamamla") }
    }
  }
}
