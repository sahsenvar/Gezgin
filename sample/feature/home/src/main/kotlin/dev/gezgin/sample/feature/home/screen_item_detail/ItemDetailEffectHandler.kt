package dev.gezgin.sample.feature.home.screen_item_detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun ItemDetailEffectHandler(effects: Flow<ItemDetailEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is ItemDetailEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
