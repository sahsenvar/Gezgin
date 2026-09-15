package dev.gezgin.sample.hello.screen_contact_list

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.hello.CONTACTS
import dev.gezgin.sample.hello.nav.HelloGraph
import dev.gezgin.sample.hello.ui.BaseViewModel
import dev.gezgin.sample.hello.ui.EffectSink
import dev.gezgin.sample.hello.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactListViewModel :
  BaseViewModel<ContactListUiState, ContactListIntent, ContactListEffect>() {

  private val _uiState = MutableStateFlow(ContactListUiState(contacts = CONTACTS))
  override val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<ContactListEffect>()
  override val effects: Flow<ContactListEffect> = _effects.flow

  override fun onIntent(intent: ContactListIntent) {
    when (intent) {
      is ContactListIntent.OpenContact -> _effects.send(ContactListEffect.OpenContact(intent.id))
    }
  }
}

@ViewModelOf(HelloGraph.ContactListScreenRoute::class)
@Composable
fun contactListViewModel(): ContactListViewModel = viewModel { ContactListViewModel() }
