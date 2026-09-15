package dev.gezgin.sample.hello.screen_contact_detail

import dev.gezgin.sample.hello.ui.UiIntent

sealed interface ContactDetailIntent : UiIntent {
  data object ToggleStar : ContactDetailIntent

  data object Back : ContactDetailIntent
}
