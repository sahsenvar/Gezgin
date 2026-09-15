package dev.gezgin.sample.hello.screen_contact_detail

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
import kotlinx.coroutines.flow.update

class ContactDetailViewModel(
    contactId: String
) : BaseViewModel<ContactDetailUiState, ContactDetailIntent, ContactDetailEffect>() {

    private val contact = CONTACTS.first { it.id == contactId }

    private val _uiState = MutableStateFlow(ContactDetailUiState(contact.name, contact.title))
    override val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    private val _effects = EffectSink<ContactDetailEffect>()
    override val effects: Flow<ContactDetailEffect> = _effects.flow

    override fun onIntent(intent: ContactDetailIntent) {
        when (intent) {
            ContactDetailIntent.ToggleStar -> _uiState.update { it.copy(starred = !it.starred) }
            ContactDetailIntent.Back -> _effects.send(ContactDetailEffect.BackToList)
        }
    }
}

/** The route reaches the ViewModel here; Gezgin supplies it, the DI call is the app's own. */
@ViewModelOf(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun contactDetailViewModel(route: HelloGraph.ContactDetailScreenRoute): ContactDetailViewModel =
    viewModel {
        ContactDetailViewModel(contactId = route.contactId)
    }
