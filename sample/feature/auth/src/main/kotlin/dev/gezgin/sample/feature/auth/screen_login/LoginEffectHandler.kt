package dev.gezgin.sample.feature.auth.screen_login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.designsystem.ResultCollector
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.LoginNavigator

@Effects(LoginScreenRoute::class)
fun handleLoginEffect(effect: LoginEffect, show: (String) -> Unit, nav: LoginNavigator) {
  when (effect) {
    is LoginEffect.ShowMessage -> show(effect.text)
    LoginEffect.LoginSuccess -> nav.loginSuccess()
    is LoginEffect.OpenForgotPassword -> nav.launchForgotPasswordDialog(effect.email)
    LoginEffect.OpenSignUp -> nav.goToSignUp()
  }
}

@ResultCollector(LoginScreenRoute::class)
@Composable
fun LoginResultCollector(onIntent: (LoginIntent) -> Unit, nav: LoginNavigator) {
  LaunchedEffect(nav) {
    nav.forgotPasswordDialogResults.collect { result ->
      onIntent(LoginIntent.ForgotPasswordResult(result))
    }
  }
}
