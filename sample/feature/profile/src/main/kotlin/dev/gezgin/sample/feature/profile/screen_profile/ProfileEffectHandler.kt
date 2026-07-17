package dev.gezgin.sample.feature.profile.screen_profile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.feature.profile.resultIntentSink
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(ProfileScreenRoute::class)
@Composable
fun ProfileEffectHandler(effects: Flow<ProfileEffect>, nav: ProfileNavigator) {
    val context = LocalContext.current
    val resultIntents = effects.resultIntentSink<ProfileIntent>()
    LaunchedEffect(effects) {
        nav.pickAvatarResults.collect { result ->
            resultIntents.sendResultIntent(ProfileIntent.AvatarResult(result))
        }
    }
    LaunchedEffect(effects) {
        nav.editNameDialogResults.collect { result ->
            resultIntents.sendResultIntent(ProfileIntent.EditNameResult(result))
        }
    }
    LaunchedEffect(effects) {
        nav.pickNotificationsResults.collect { result ->
            resultIntents.sendResultIntent(ProfileIntent.NotificationsResult(result))
        }
    }
    ObserveEffects(effects) { effect ->
        when (effect) {
            is ProfileEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            else -> handleProfileEffect(effect, nav)
        }
    }
}

internal fun handleProfileEffect(effect: ProfileEffect, nav: ProfileNavigator) {
    when (effect) {
        is ProfileEffect.ShowMessage -> Unit
        is ProfileEffect.EditName -> nav.launchEditNameDialog(effect.current)
        ProfileEffect.OpenSettings -> nav.goToSettings()
        ProfileEffect.PickAvatar -> nav.launchPickAvatar()
        is ProfileEffect.PickNotifications -> nav.launchPickNotifications(effect.current)
    }
}
