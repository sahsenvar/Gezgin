package dev.gezgin.sample.hello.screen_contact_list

sealed interface ContactListEffect {
  data class OpenContact(val id: String) : ContactListEffect
}
