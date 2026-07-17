package dev.gezgin.sample.feature.profile

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.feature.profile.screen_profile.ProfileEffect
import dev.gezgin.sample.feature.profile.screen_profile.ProfileEffectHandler
import dev.gezgin.sample.feature.profile.screen_profile.ProfileIntent
import dev.gezgin.sample.feature.profile.screen_profile.ProfileViewModel
import dev.gezgin.sample.feature.profile.screen_profile.handleProfileEffect
import dev.gezgin.sample.navigation.ProfileGraph.EditNameDialogRoute
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.editNameDialogNavigator
import dev.gezgin.sample.navigation.gezginTopology
import dev.gezgin.sample.navigation.profileNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StrictMviMigrationTest {

    @Test
    fun `profile navigation intent becomes an effect before the typed handler navigates`() = runBlocking {
        val viewModel = ProfileViewModel()

        viewModel.onIntent(ProfileIntent.OpenSettings)

        val effect = viewModel.effects.first()
        assertEquals(ProfileEffect.OpenSettings, effect)

        val raw = RawNavigator(start = ProfileScreenRoute, topology = gezginTopology)
        handleProfileEffect(effect, raw.profileNavigator(entryId = 1L))
        assertEquals(SettingsScreenRoute, raw.current)
    }

    @Test
    fun `avatar result re-enters the ViewModel as an intent`() = runBlocking {
        val viewModel = ProfileViewModel()
        val choice = AvatarChoice("file://avatar.png")

        viewModel.effects
            .resultIntentSink<ProfileIntent>()
            .sendResultIntent(ProfileIntent.AvatarResult(NavResult.Value(choice)))

        assertEquals(choice.uri, viewModel.uiState.value.avatarUri)
        assertEquals(ProfileEffect.ShowMessage("Avatar güncellendi"), viewModel.effects.first())
    }

    @OptIn(GezginInternalApi::class)
    @Test
    fun `route-bound profile collector detaches and reattaches once to a persisted result`() {
        val viewModel = ProfileViewModel()
        var deliveredIntents = 0
        val routedEffects = resultIntentEffectFlow<ProfileEffect, ProfileIntent>(viewModel.effects) { intent ->
            deliveredIntents += 1
            viewModel.onIntent(intent)
        }
        val raw = RawNavigator(start = ProfileScreenRoute, topology = gezginTopology)
        val nav = raw.profileNavigator(entryId = requireNotNull(raw.entryIdOf(ProfileScreenRoute::class)))
        val attached = mutableStateOf(true)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

        try {
            controller.get().setContent {
                if (attached.value) ProfileEffectHandler(routedEffects, nav)
            }
            shadowOf(Looper.getMainLooper()).idle()

            controller.get().runOnUiThread { attached.value = false }
            shadowOf(Looper.getMainLooper()).idle()

            nav.launchEditNameDialog(current = viewModel.uiState.value.name)
            raw.editNameDialogNavigator(
                entryId = requireNotNull(raw.entryIdOf(EditNameDialogRoute::class)),
            ).backWithResult("Ada Lovelace")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, deliveredIntents, "detached collector must not receive results")

            controller.get().runOnUiThread { attached.value = true }
            awaitComposeCondition("reattached collector did not receive the persisted result") {
                viewModel.uiState.value.name == "Ada Lovelace"
            }
            assertEquals(1, deliveredIntents, "reattach must leave exactly one active result collector")
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
