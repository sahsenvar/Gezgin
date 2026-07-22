package dev.gezgin.sample.feature.auth.screen_credentials

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.CredentialsNavigator
import dev.gezgin.sample.navigation.SignUpFlow.CredentialsScreenRoute
import kotlinx.coroutines.flow.Flow

@EffectHandler(CredentialsScreenRoute::class)
@Composable
fun CredentialsEffectHandler(effects: Flow<CredentialsEffect>, nav: CredentialsNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    when (effect) {
      is CredentialsEffect.ShowMessage ->
        Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      is CredentialsEffect.OpenProfileInfo -> nav.goToProfileInfo(effect.email)
    }
  }
}
