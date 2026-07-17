package dev.gezgin.sample.feature.profile.screen_crop

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.CropNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(CropScreenRoute::class)
@Composable
fun CropEffectHandler(effects: Flow<CropEffect>, nav: CropNavigator) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is CropEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            CropEffect.OpenZoom -> nav.goToZoom()
            is CropEffect.Complete -> nav.quitWith(effect.choice)
            CropEffect.Back -> nav.back()
        }
    }
}
