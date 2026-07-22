package dev.gezgin.sample.feature.profile.sheet_notification

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.NotificationsSheetNavigator
import dev.gezgin.sample.navigation.ProfileGraph.NotificationsSheetRoute
import kotlinx.coroutines.flow.Flow

@EffectHandler(NotificationsSheetRoute::class)
@Composable
fun NotificationsEffectHandler(
  effects: Flow<NotificationsEffect>,
  nav: NotificationsSheetNavigator,
) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    when (effect) {
      is NotificationsEffect.ShowMessage ->
        Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      is NotificationsEffect.Confirm -> nav.backWithResult(effect.level)
    }
  }
}
