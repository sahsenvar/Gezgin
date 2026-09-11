package dev.gezgin.sample.hello.screen_contact_detail

import dev.gezgin.sample.hello.ui.UiEvent

sealed interface ContactDetailEffect : UiEvent {
  data object BackToList : ContactDetailEffect
}
