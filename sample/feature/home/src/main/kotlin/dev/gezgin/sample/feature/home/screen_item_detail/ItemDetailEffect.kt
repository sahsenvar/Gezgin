package dev.gezgin.sample.feature.home.screen_item_detail

sealed interface ItemDetailEffect {
  data class ShowMessage(val text: String) : ItemDetailEffect

  data class OpenRelated(val id: String) : ItemDetailEffect

  data class OpenImage(val id: String) : ItemDetailEffect

  data object BackToDashboard : ItemDetailEffect

  data class ConfirmDelete(val id: String) : ItemDetailEffect

  data class ChooseShareTarget(val id: String) : ItemDetailEffect
}
