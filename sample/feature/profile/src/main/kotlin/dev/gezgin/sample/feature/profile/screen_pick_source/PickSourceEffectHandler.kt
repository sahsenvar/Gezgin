package dev.gezgin.sample.feature.profile.screen_pick_source

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.AvatarFlow.PickSourceScreenRoute
import dev.gezgin.sample.navigation.PickSourceNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(PickSourceScreenRoute::class)
@Composable
fun PickSourceEffectHandler(effects: Flow<PickSourceEffect>, nav: PickSourceNavigator) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is PickSourceEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            is PickSourceEffect.OpenCrop -> nav.goToCrop(effect.source)
        }
    }
}
