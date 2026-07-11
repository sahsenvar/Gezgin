package dev.gezgin.sample.shopr.screen_cart

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun CartEffectHandler(effects: Flow<CartEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is CartEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
