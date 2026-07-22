package dev.gezgin.sample.feature.profile.screen_pick_source

sealed interface PickSourceEffect {
  data class ShowMessage(val text: String) : PickSourceEffect

  data class OpenCrop(val source: String) : PickSourceEffect
}
