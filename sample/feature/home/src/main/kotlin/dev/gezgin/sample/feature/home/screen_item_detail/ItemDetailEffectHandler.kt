package dev.gezgin.sample.feature.home.screen_item_detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(ItemDetailScreenRoute::class)
@Composable
fun ItemDetailEffectHandler(effects: Flow<ItemDetailEffect>, nav: ItemDetailNavigator) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is ItemDetailEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            is ItemDetailEffect.OpenRelated -> nav.goToRelated(effect.id)
            is ItemDetailEffect.OpenImage -> nav.goToItemImageViewer(effect.id)
            ItemDetailEffect.BackToDashboard -> nav.backToDashboard()
        }
    }
}
