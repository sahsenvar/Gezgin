package dev.gezgin.sample.feature.home.screen_item_detail

sealed interface ItemDetailIntent {
  data object OnAppear : ItemDetailIntent

  data object OpenRelated : ItemDetailIntent

  data object OpenImage : ItemDetailIntent

  data object Back : ItemDetailIntent
}
