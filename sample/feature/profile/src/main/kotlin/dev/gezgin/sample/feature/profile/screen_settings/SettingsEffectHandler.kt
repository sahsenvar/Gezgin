package dev.gezgin.sample.feature.profile.screen_settings

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.SettingsNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(SettingsScreenRoute::class)
@Composable
fun SettingsEffectHandler(effects: Flow<SettingsEffect>, nav: SettingsNavigator) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage -> {
                Log.d("SettingsMvi", "effect: ${effect.text}")
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
            SettingsEffect.Logout -> nav.logout()
        }
    }
}
