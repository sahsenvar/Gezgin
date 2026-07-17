package dev.gezgin.core.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private val androidTestJson = Json { serializersModule = testSerializersModule }

@RunWith(RobolectricTestRunner::class)
class RememberNavigatorAndroidIdentityTest {
    @Test
    fun `retained owner reuses same restoreKey holder and isolates changed restoreKey snapshot`() {
        val owner = RetainedOwner()
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = activityController.get()
        try {
            val initial = composeNavigator(activity, owner, restoredValues = null, restoreKey = "account-42", start = Feed)
            initial.navigator.navigate(Catalog)

            val recreationState = initial.registry.performSave()
            initial.view.disposeComposition()
            val sameKey = composeNavigator(activity, owner, recreationState, restoreKey = "account-42", start = Feed)

            assertSame(initial.navigator, sameKey.navigator)
            assertEquals(listOf<Route>(Feed, Catalog), sameKey.navigator.keys.map { it.route })

            sameKey.view.disposeComposition()
            val changedKey = composeNavigator(
                activity = activity,
                owner = owner,
                restoredValues = recreationState,
                restoreKey = "account-99",
                start = Product("fresh"),
            )

            assertNotSame(initial.navigator, changedKey.navigator)
            assertEquals(listOf<Route>(Product("fresh")), changedKey.navigator.keys.map { it.route })
        } finally {
            owner.viewModelStore.clear()
            activityController.pause().stop().destroy()
        }
    }

    private fun composeNavigator(
        activity: ComponentActivity,
        owner: RetainedOwner,
        restoredValues: Map<String, List<Any?>>?,
        restoreKey: String,
        start: Route,
    ): NavigatorComposition {
        val registry = SaveableStateRegistry(restoredValues = restoredValues, canBeSaved = { true })
        var navigator: RawNavigator? = null
        val view = ComposeView(activity)
        activity.setContentView(view)
        view.setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalSaveableStateRegistry provides registry,
            ) {
                navigator = rememberRawNavigatorInstance(
                    start = start,
                    topology = testTopology,
                    json = androidTestJson,
                    restoreKey = restoreKey,
                    onRootBack = {},
                )
            }
        }

        return NavigatorComposition(checkNotNull(navigator), registry, view)
    }

    private class RetainedOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class NavigatorComposition(
        val navigator: RawNavigator,
        val registry: SaveableStateRegistry,
        val view: ComposeView,
    )
}
