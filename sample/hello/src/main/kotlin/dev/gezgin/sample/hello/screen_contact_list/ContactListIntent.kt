package dev.gezgin.sample.hello.screen_contact_list

import dev.gezgin.sample.hello.ui.UiIntent

sealed interface ContactListIntent : UiIntent {
  data class OpenContact(val id: String) : ContactListIntent
}
