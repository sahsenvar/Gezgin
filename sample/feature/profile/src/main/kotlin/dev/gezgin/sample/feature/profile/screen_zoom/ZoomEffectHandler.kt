package dev.gezgin.sample.feature.profile.screen_zoom

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ZoomNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(ZoomScreenRoute::class)
@Composable
fun ZoomEffectHandler(effects: Flow<ZoomEffect>, nav: ZoomNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    when (effect) {
      is ZoomEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      is ZoomEffect.Complete -> nav.quitWith(effect.choice)
      ZoomEffect.Back -> nav.back()
    }
  }
}
