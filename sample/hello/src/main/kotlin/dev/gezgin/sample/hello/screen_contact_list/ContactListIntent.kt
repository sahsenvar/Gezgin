package dev.gezgin.sample.hello.screen_contact_list

sealed interface ContactListIntent {
  data class OpenContact(val id: String) : ContactListIntent
}
