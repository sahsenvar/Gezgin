package dev.gezgin.sample.hello.screen_contact_detail

sealed interface ContactDetailEffect {
  data class ShowMessage(val text: String) : ContactDetailEffect

  data object BackToList : ContactDetailEffect
}
