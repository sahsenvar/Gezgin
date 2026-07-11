package dev.gezgin.sample.feature.profile.sheet_notification

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.ScreenEffect
import kotlinx.coroutines.flow.Flow

@ScreenEffect
@Composable
fun NotificationsEffectHandler(effects: Flow<NotificationsEffect>) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is NotificationsEffect.ShowMessage -> Toast.makeText(
                context,
                effect.text,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}