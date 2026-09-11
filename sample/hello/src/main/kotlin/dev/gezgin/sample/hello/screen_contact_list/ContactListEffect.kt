package dev.gezgin.sample.hello.screen_contact_list

import dev.gezgin.sample.hello.ui.UiEvent

sealed interface ContactListEffect : UiEvent {
  data class OpenContact(val id: String) : ContactListEffect
}
