package dev.gezgin.sample.feature.auth

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.feature.auth.screen_login.LoginEffect
import dev.gezgin.sample.feature.auth.screen_login.LoginIntent
import dev.gezgin.sample.feature.auth.screen_login.LoginViewModel
import dev.gezgin.sample.feature.auth.screen_login.handleLoginEffect
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.gezginTopology
import dev.gezgin.sample.navigation.loginNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrictMviMigrationTest {

    @Test
    fun `login navigation intent becomes an effect before the typed handler navigates`() = runBlocking {
        val viewModel = LoginViewModel()

        viewModel.onIntent(LoginIntent.Submit)

        val effect = viewModel.effects.first()
        assertEquals(LoginEffect.LoginSuccess, effect)

        val raw = RawNavigator(start = LoginScreenRoute, topology = gezginTopology)
        handleLoginEffect(effect, raw.loginNavigator(entryId = 1L))
        assertEquals(DashboardScreenRoute, raw.current)
    }

    @Test
    fun `forgot password result re-enters the ViewModel as an intent`() = runBlocking {
        val viewModel = LoginViewModel()

        viewModel.effects
            .resultIntentSink<LoginIntent>()
            .sendResultIntent(LoginIntent.ForgotPasswordResult(NavResult.Value(true)))

        assertEquals(LoginEffect.ShowMessage("Sıfırlama linki gönderildi"), viewModel.effects.first())
    }
}
