package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
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

private val jvmSaveableTestJson = Json { serializersModule = testSerializersModule }

@OptIn(ExperimentalTestApi::class)
class RememberNavigatorJvmSaveableRegistryTest {
    @Test
    fun `rememberNavigator restores same namespace through production saveable path`() {
        val initial = composeAndSave(
            restoredValues = null,
            restoreKey = "account-42",
            start = Feed,
            usePublicEntryPoint = true,
        ) { it.navigate(Catalog) }

        val restored = composeAndSave(
            restoredValues = initial.savedValues,
            restoreKey = "account-42",
            start = Product("fresh"),
            usePublicEntryPoint = true,
        )

        assertNotSame(initial.navigator, restored.navigator)
        assertEquals(listOf<Route>(Feed, Catalog), restored.navigator.keys.map { it.route })
    }

    @Test
    fun `rememberRawNavigatorInstance rejects Aa payload for BB composite hash collision`() {
        assertEquals("Aa".hashCode(), "BB".hashCode(), "test keys must exercise a real String hash collision")
        val initial = composeAndSave(
            restoredValues = null,
            restoreKey = "Aa",
            start = Feed,
            usePublicEntryPoint = false,
        ) { it.navigate(Catalog) }

        val changed = composeAndSave(
            restoredValues = initial.savedValues,
            restoreKey = "BB",
            start = Product("fresh"),
            usePublicEntryPoint = false,
        )

        assertEquals(listOf<Route>(Product("fresh")), changed.navigator.keys.map { it.route })
    }

    private fun composeAndSave(
        restoredValues: Map<String, List<Any?>>?,
        restoreKey: String,
        start: Route,
        usePublicEntryPoint: Boolean,
        mutate: (RawNavigator) -> Unit = {},
    ): SavedComposition {
        val registry = SaveableStateRegistry(restoredValues = restoredValues, canBeSaved = { true })
        var navigator: RawNavigator? = null
        var savedValues: Map<String, List<Any?>>? = null

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    navigator = productionNavigator(start, restoreKey, usePublicEntryPoint)
                }
            }
            waitForIdle()
            val current = checkNotNull(navigator)
            mutate(current)
            waitForIdle()
            savedValues = registry.performSave()
        }

        return SavedComposition(checkNotNull(navigator), checkNotNull(savedValues))
    }

    @Composable
    private fun productionNavigator(
        start: Route,
        restoreKey: String,
        usePublicEntryPoint: Boolean,
    ): RawNavigator = if (usePublicEntryPoint) {
        rememberNavigator(
            start = start,
            topology = testTopology,
            json = jvmSaveableTestJson,
            restoreKey = restoreKey,
        )
    } else {
        rememberRawNavigatorInstance(
            start = start,
            topology = testTopology,
            json = jvmSaveableTestJson,
            restoreKey = restoreKey,
            onRootBack = {},
        )
    }

    private data class SavedComposition(
        val navigator: RawNavigator,
        val savedValues: Map<String, List<Any?>>,
    )
}
