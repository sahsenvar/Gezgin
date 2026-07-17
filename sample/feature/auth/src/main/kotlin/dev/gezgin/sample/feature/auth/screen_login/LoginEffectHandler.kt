package dev.gezgin.sample.feature.auth.screen_login

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.feature.auth.resultIntentSink
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.LoginNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(LoginScreenRoute::class)
@Composable
fun LoginEffectHandler(effects: Flow<LoginEffect>, nav: LoginNavigator) {
    val context = LocalContext.current
    val resultIntents = effects.resultIntentSink<LoginIntent>()
    LaunchedEffect(effects) {
        nav.forgotPasswordDialogResults.collect { result ->
            resultIntents.sendResultIntent(LoginIntent.ForgotPasswordResult(result))
        }
    }
    ObserveEffects(effects) { effect ->
        when (effect) {
            is LoginEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            else -> handleLoginEffect(effect, nav)
        }
    }
}

internal fun handleLoginEffect(effect: LoginEffect, nav: LoginNavigator) {
    when (effect) {
        is LoginEffect.ShowMessage -> Unit
        LoginEffect.LoginSuccess -> nav.loginSuccess()
        is LoginEffect.OpenForgotPassword -> nav.launchForgotPasswordDialog(effect.email)
        LoginEffect.OpenSignUp -> nav.goToSignUp()
    }
}
