package dev.gezgin.sample.hello.screen_contact_detail

sealed interface ContactDetailIntent {
  data object ToggleStar : ContactDetailIntent

  data object Back : ContactDetailIntent
}
