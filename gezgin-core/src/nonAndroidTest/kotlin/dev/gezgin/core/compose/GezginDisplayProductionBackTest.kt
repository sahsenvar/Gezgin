package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlinx.serialization.json.Json

private val productionBackTestJson = Json { serializersModule = testSerializersModule }

/**
 * Applications reach the back stack through [rememberNavigator] and [GezginDisplay] together. The
 * other display tests construct the navigator directly and the saveable tests leave the display
 * out, so this combination — the one that ships — is asserted here, on every platform the suite
 * runs on.
 */
@OptIn(ExperimentalTestApi::class)
class GezginDisplayProductionBackTest {

  @Test
  fun `back returns to the previous screen through the production entry point`() =
    runComposeUiTest {
      var navigator: RawNavigator? = null
      setContent {
        val nav =
          rememberNavigator(
            start = Feed,
            topology = testTopology,
            json = productionBackTestJson,
            restoreKey = "production-back",
          )
        navigator = nav
        GezginDisplay(navigator = nav) {
          register<Feed> { BasicText("FeedScreen") }
          register<Catalog> { BasicText("CatalogScreen") }
        }
      }
      waitForIdle()
      val nav = checkNotNull(navigator)

      nav.navigate(Catalog)
      waitForIdle()
      onNodeWithText("CatalogScreen").assertIsDisplayed()

      nav.back()
      waitForIdle()
      onNodeWithText("FeedScreen").assertIsDisplayed()
      onAllNodesWithText("CatalogScreen").assertCountEquals(0)
    }

  @Test
  fun `backTo finds its target through the production entry point`() = runComposeUiTest {
    var navigator: RawNavigator? = null
    setContent {
      val nav =
        rememberNavigator(
          start = Feed,
          topology = testTopology,
          json = productionBackTestJson,
          restoreKey = "production-back-to",
        )
      navigator = nav
      GezginDisplay(navigator = nav) {
        register<Feed> { BasicText("FeedScreen") }
        register<Catalog> { BasicText("CatalogScreen") }
      }
    }
    waitForIdle()
    val nav = checkNotNull(navigator)

    nav.navigate(Catalog)
    waitForIdle()
    onNodeWithText("CatalogScreen").assertIsDisplayed()

    // The sample's top-bar back is a @BackTo edge, so the generated call lands here.
    nav.backTo(Feed::class)
    waitForIdle()
    onNodeWithText("FeedScreen").assertIsDisplayed()
    onAllNodesWithText("CatalogScreen").assertCountEquals(0)
  }
}
