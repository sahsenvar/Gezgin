package dev.gezgin.sample.feature.home.screen_welcome

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.HomeGraph.WelcomeScreenRoute
import dev.gezgin.sample.navigation.WelcomeNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(WelcomeScreenRoute::class)
@Composable
fun WelcomeEffectHandler(effects: Flow<WelcomeEffect>, nav: WelcomeNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    when (effect) {
      is WelcomeEffect.ShowMessage ->
        Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      WelcomeEffect.ContinueToDashboard -> nav.continueToDashboard()
    }
  }
}
