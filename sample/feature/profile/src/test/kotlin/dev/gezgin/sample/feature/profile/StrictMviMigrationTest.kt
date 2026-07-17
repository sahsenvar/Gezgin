package dev.gezgin.sample.feature.profile

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.feature.profile.screen_profile.ProfileEffect
import dev.gezgin.sample.feature.profile.screen_profile.ProfileIntent
import dev.gezgin.sample.feature.profile.screen_profile.ProfileViewModel
import dev.gezgin.sample.feature.profile.screen_profile.handleProfileEffect
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.gezginTopology
import dev.gezgin.sample.navigation.profileNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
