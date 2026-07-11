package dev.gezgin.sample.shopr.screen_catalog

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun CatalogEffectHandler(effects: Flow<CatalogEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is CatalogEffect.Message -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
