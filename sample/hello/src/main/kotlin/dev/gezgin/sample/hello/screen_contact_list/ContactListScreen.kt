package dev.gezgin.sample.hello.screen_contact_list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.hello.nav.HelloGraph

@Screen(HelloGraph.ContactListScreenRoute::class)
@Composable
fun ContactListScreen(state: ContactListUiState, onIntent: (ContactListIntent) -> Unit) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Kişiler")
      state.contacts.forEach { contact ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          onClick = { onIntent(ContactListIntent.OpenContact(contact.id)) },
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(contact.name)
            Text(contact.title)
          }
        }
      }
    }
  }
}
