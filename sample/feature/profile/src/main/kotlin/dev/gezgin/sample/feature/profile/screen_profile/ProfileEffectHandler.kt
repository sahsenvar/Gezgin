package dev.gezgin.sample.feature.profile.screen_profile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun ProfileEffectHandler(effects: Flow<ProfileEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is ProfileEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }
}
