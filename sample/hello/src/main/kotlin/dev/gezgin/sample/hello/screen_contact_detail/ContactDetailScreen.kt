package dev.gezgin.sample.hello.screen_contact_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.hello.nav.HelloGraph

@Screen(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun ContactDetailScreen(state: ContactDetailUiState, onIntent: (ContactDetailIntent) -> Unit) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(state.name)
      Text(state.title)
      Button(onClick = { onIntent(ContactDetailIntent.ToggleStar) }) {
        Text(if (state.starred) "Favorilerde ★" else "Favorilere ekle ☆")
      }
      TextButton(onClick = { onIntent(ContactDetailIntent.Back) }) { Text("Listeye dön") }
    }
  }
}
