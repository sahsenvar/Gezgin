package dev.gezgin.sample.feature.auth

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.feature.auth.screen_login.LoginEffect
import dev.gezgin.sample.feature.auth.screen_login.LoginEffectHandler
import dev.gezgin.sample.feature.auth.screen_login.LoginIntent
import dev.gezgin.sample.feature.auth.screen_login.LoginViewModel
import dev.gezgin.sample.feature.auth.screen_login.handleLoginEffect
import dev.gezgin.sample.navigation.AuthGraph.ForgotPasswordDialogRoute
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.forgotPasswordDialogNavigator
import dev.gezgin.sample.navigation.gezginTopology
import dev.gezgin.sample.navigation.loginNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
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

    @OptIn(GezginInternalApi::class)
    @Test
    fun `route-bound login collector delivers a persisted result through the ViewModel`() {
        ShadowToast.reset()
        val viewModel = LoginViewModel()
        val raw = RawNavigator(start = LoginScreenRoute, topology = gezginTopology)
        val nav = raw.loginNavigator(entryId = requireNotNull(raw.entryIdOf(LoginScreenRoute::class)))

        nav.launchForgotPasswordDialog(email = null)
        raw.forgotPasswordDialogNavigator(
            entryId = requireNotNull(raw.entryIdOf(ForgotPasswordDialogRoute::class)),
        ).backWithResult(true)

        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent { LoginEffectHandler(viewModel.effects, nav) }

            awaitComposeCondition("persisted forgot-password result was not handled") {
                ShadowToast.getTextOfLatestToast() == "Sıfırlama linki gönderildi"
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

private fun awaitComposeCondition(message: String, condition: () -> Boolean) {
    repeat(100) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(10)
    }
    assertTrue(condition(), message)
}
