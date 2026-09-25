package dev.gezgin.sample.feature.home.screen_item_detail

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator

@Effects(ItemDetailScreenRoute::class)
fun handleItemDetailEffect(
  effect: ItemDetailEffect,
  show: (String) -> Unit,
  onIntent: (ItemDetailIntent) -> Unit,
  nav: ItemDetailNavigator,
) {
  when (effect) {
    is ItemDetailEffect.ShowMessage -> show(effect.text)
    is ItemDetailEffect.OpenRelated -> nav.goToRelated(effect.id)
    is ItemDetailEffect.OpenImage -> nav.goToItemImageViewer(effect.id)
    ItemDetailEffect.BackToDashboard -> nav.backToDashboard()
    is ItemDetailEffect.ConfirmDelete ->
      nav.openDeleteItemDialog(
        itemId = effect.id,
        onConfirm = {
          nav.back()
          onIntent(ItemDetailIntent.DeleteConfirmed)
        },
        onCancel = {
          onIntent(ItemDetailIntent.DeleteCancelled)
          nav.back()
        },
      )
    is ItemDetailEffect.ChooseShareTarget ->
      nav.openShareTargetSheet(
        itemId = effect.id,
        onSelect = { target ->
          onIntent(ItemDetailIntent.ShareTargetChosen(target))
          nav.back()
        },
      )
  }
}
