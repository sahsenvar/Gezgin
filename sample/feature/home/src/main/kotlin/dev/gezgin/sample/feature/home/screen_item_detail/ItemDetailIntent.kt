package dev.gezgin.sample.feature.home.screen_item_detail

import dev.gezgin.sample.domain.model.ShareTarget

sealed interface ItemDetailIntent {
  data object OnAppear : ItemDetailIntent

  data object OpenRelated : ItemDetailIntent

  data object OpenImage : ItemDetailIntent

  data object Back : ItemDetailIntent

  data object DeleteRequested : ItemDetailIntent

  data object DeleteConfirmed : ItemDetailIntent

  data object DeleteCancelled : ItemDetailIntent

  data object ShareRequested : ItemDetailIntent

  data class ShareTargetChosen(val target: ShareTarget) : ItemDetailIntent
}
