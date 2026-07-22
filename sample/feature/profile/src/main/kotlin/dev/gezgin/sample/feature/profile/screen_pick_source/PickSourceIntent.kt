package dev.gezgin.sample.feature.profile.screen_pick_source

sealed interface PickSourceIntent {
  data object PickGallery : PickSourceIntent

  data object PickCamera : PickSourceIntent
}
