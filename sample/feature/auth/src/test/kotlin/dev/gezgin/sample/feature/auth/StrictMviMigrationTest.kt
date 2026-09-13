package dev.gezgin.sample.feature.auth

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.feature.auth.screen_login.LoginEffect
import dev.gezgin.sample.feature.auth.screen_login.LoginIntent
import dev.gezgin.sample.feature.auth.screen_login.LoginResultCollector
import dev.gezgin.sample.feature.auth.screen_login.LoginViewModel
import dev.gezgin.sample.feature.auth.screen_login.handleLoginEffect
import dev.gezgin.sample.navigation.AuthGraph.ForgotPasswordDialogRoute
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.forgotPasswordDialogNavigator
import dev.gezgin.sample.navigation.gezginTopology
import dev.gezgin.sample.navigation.loginNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The one-directional contract survives the move off `gezgin-mvi`: an intent becomes an effect, the
 * route-bound effect provider owns the typed navigation, and a persisted result re-enters the
 * ViewModel through the result-collector slot.
 */
@RunWith(RobolectricTestRunner::class)
class StrictMviMigrationTest {

  @Test
  fun `login navigation intent becomes an effect before the typed provider navigates`() =
    runBlocking {
      val viewModel = LoginViewModel()

      viewModel.onIntent(LoginIntent.Submit)

      val effect = viewModel.effects.first()
      assertEquals(LoginEffect.LoginSuccess, effect)

      val raw = RawNavigator(start = LoginScreenRoute, topology = gezginTopology)
      handleLoginEffect(effect, {}, raw.loginNavigator(entryId = 1L))
      assertEquals(DashboardScreenRoute, raw.current)
    }

  @Test
  fun `forgot password result re-enters the ViewModel as an intent`() = runBlocking {
    val viewModel = LoginViewModel()

    viewModel.onIntent(LoginIntent.ForgotPasswordResult(NavResult.Value(true)))

    assertEquals(LoginEffect.ShowMessage("Sıfırlama linki gönderildi"), viewModel.effects.first())
  }

  @OptIn(GezginInternalApi::class)
  @Test
  fun `route-bound login collector delivers a persisted result through the ViewModel`() {
    val viewModel = LoginViewModel()
    val raw = RawNavigator(start = LoginScreenRoute, topology = gezginTopology)
    val nav = raw.loginNavigator(entryId = requireNotNull(raw.entryIdOf(LoginScreenRoute::class)))
    val messages = mutableListOf<String>()

    nav.launchForgotPasswordDialog(email = null)
    raw
      .forgotPasswordDialogNavigator(
        entryId = requireNotNull(raw.entryIdOf(ForgotPasswordDialogRoute::class))
      )
      .backWithResult(true)

    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    try {
      // Exactly the wiring ShowcaseScreenRoot performs for this route.
      controller.get().setContent {
        LaunchedEffect(viewModel) {
          viewModel.effects.collect { effect -> handleLoginEffect(effect, messages::add, nav) }
        }
        LoginResultCollector(onIntent = viewModel::onIntent, nav = nav)
      }

      awaitComposeCondition("persisted forgot-password result was not handled") {
        messages.lastOrNull() == "Sıfırlama linki gönderildi"
      }
    } finally {
      controller.get().runOnUiThread { controller.get().setContent {} }
      shadowOf(Looper.getMainLooper()).idle()
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
