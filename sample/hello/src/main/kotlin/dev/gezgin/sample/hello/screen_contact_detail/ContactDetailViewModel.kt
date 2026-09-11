package dev.gezgin.sample.hello.screen_contact_detail

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
import kotlinx.coroutines.flow.update

@MviViewModel(HelloGraph.ContactDetailScreenRoute::class)
class ContactDetailViewModel(route: HelloGraph.ContactDetailScreenRoute) :
  ViewModel(), GezginMvi<ContactDetailUiState, ContactDetailIntent, ContactDetailEffect> {

  private val contact = CONTACTS.first { it.id == route.contactId }

  private val _uiState = MutableStateFlow(ContactDetailUiState(contact.name, contact.title))
  override val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

  private val _effects = GezginEffects<ContactDetailEffect>()
  override val effects: Flow<ContactDetailEffect> = _effects.flow

  override fun onIntent(intent: ContactDetailIntent) {
    when (intent) {
      ContactDetailIntent.ToggleStar -> {
        _uiState.update { it.copy(starred = !it.starred) }
        val text = if (_uiState.value.starred) "Favorilere eklendi" else "Favorilerden çıkarıldı"
        _effects.send(ContactDetailEffect.ShowMessage(text))
      }
      ContactDetailIntent.Back -> _effects.send(ContactDetailEffect.BackToList)
    }
  }
}
