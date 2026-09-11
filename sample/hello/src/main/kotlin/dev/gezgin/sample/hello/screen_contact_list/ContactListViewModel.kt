package dev.gezgin.sample.hello.screen_contact_list

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.hello.CONTACTS
import dev.gezgin.sample.hello.nav.HelloGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(HelloGraph.ContactListScreenRoute::class)
class ContactListViewModel :
  ViewModel(), GezginMvi<ContactListUiState, ContactListIntent, ContactListEffect> {

  private val _uiState = MutableStateFlow(ContactListUiState(contacts = CONTACTS))
  override val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<ContactListEffect>()
  override val effects: Flow<ContactListEffect> = _effects.flow

  override fun onIntent(intent: ContactListIntent) {
    when (intent) {
      is ContactListIntent.OpenContact -> _effects.send(ContactListEffect.OpenContact(intent.id))
    }
  }
}
