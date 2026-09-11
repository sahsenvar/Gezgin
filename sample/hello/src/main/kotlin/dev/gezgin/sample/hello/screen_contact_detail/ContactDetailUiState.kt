package dev.gezgin.sample.hello.screen_contact_detail

import dev.gezgin.sample.hello.ui.UiState

data class ContactDetailUiState(val name: String, val title: String, val starred: Boolean = false) :
  UiState
