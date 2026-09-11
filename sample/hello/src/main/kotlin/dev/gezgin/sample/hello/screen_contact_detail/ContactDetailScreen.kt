package dev.gezgin.sample.hello.screen_contact_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.gezgin.sample.hello.ui.TopBar

@Screen(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun ColumnScope.ContactDetailScreen(
  state: ContactDetailUiState,
  onIntent: (ContactDetailIntent) -> Unit,
) {
  Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(state.title)
    Button(onClick = { onIntent(ContactDetailIntent.ToggleStar) }) {
      Text(if (state.starred) "Favorilerde ★" else "Favorilere ekle ☆")
    }
  }
}

/** Fills the wrapper's `topBar` slot for this route only; the list screen leaves it defaulted. */
@TopBar(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun ContactDetailTopBar(state: ContactDetailUiState, onIntent: (ContactDetailIntent) -> Unit) {
  Surface(tonalElevation = 3.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      TextButton(onClick = { onIntent(ContactDetailIntent.Back) }) { Text("‹ Kişiler") }
      Text(state.name)
    }
  }
}
