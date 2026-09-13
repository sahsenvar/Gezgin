package dev.gezgin.sample.feature.home.screen_item_detail

import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator

@Effects(ItemDetailScreenRoute::class)
fun handleItemDetailEffect(
  effect: ItemDetailEffect,
  show: (String) -> Unit,
  nav: ItemDetailNavigator,
) {
  when (effect) {
    is ItemDetailEffect.ShowMessage -> show(effect.text)
    is ItemDetailEffect.OpenRelated -> nav.goToRelated(effect.id)
    is ItemDetailEffect.OpenImage -> nav.goToItemImageViewer(effect.id)
    ItemDetailEffect.BackToDashboard -> nav.backToDashboard()
  }
}
