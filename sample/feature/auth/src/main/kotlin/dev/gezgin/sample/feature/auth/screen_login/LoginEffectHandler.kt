package dev.gezgin.sample.feature.auth.screen_login

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun LoginEffectHandler(effects: Flow<LoginEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is LoginEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
