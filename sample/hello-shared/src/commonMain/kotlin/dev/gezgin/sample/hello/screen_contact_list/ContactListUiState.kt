package dev.gezgin.sample.hello.screen_contact_list

import dev.gezgin.sample.hello.Contact
import dev.gezgin.sample.hello.ui.UiState

data class ContactListUiState(val contacts: List<Contact> = emptyList()) : UiState
