package dev.gezgin.core.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidNavDisplayAdapterTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `adapter preserves opaque content keys metadata order identity and content`() {
    val opaqueKey = OpaqueContentKey("first")
    val firstMetadata = mapOf("first" to Any())
    val secondMetadata = mapOf("second" to Any())
    val first =
      NavEntry<Route>(key = FirstRoute, contentKey = opaqueKey, metadata = firstMetadata) {
        BasicText("first-content")
      }
    val second =
      NavEntry<Route>(key = SecondRoute, contentKey = "second", metadata = secondMetadata) {
        BasicText("second-content")
      }

    val adapted = adaptAndroidNavDisplayEntries(listOf(first, second))

    assertEquals(2, adapted.backStack.size)
    val adaptedFirst = adapted.entriesByKey.getValue(adapted.backStack[0])
    val adaptedSecond = adapted.entriesByKey.getValue(adapted.backStack[1])
    assertSame(opaqueKey, adaptedFirst.contentKey)
    assertSame(firstMetadata, adaptedFirst.metadata)
    assertEquals("second", adaptedSecond.contentKey)
    assertSame(secondMetadata, adaptedSecond.metadata)

    composeRule.setContent { adaptedFirst.Content() }
    composeRule.onNodeWithText("first-content").assertIsDisplayed()
  }

  @Test
  fun `adapter preserves duplicate opaque content keys as separate ordered entries`() {
    val opaqueKey = OpaqueContentKey("shared")
    val firstMetadata = mapOf("owner" to "first")
    val secondMetadata = mapOf("owner" to "second")
    val first =
      NavEntry<Route>(key = FirstRoute, contentKey = opaqueKey, metadata = firstMetadata) {
        BasicText("duplicate-first-content")
      }
    val second =
      NavEntry<Route>(key = SecondRoute, contentKey = opaqueKey, metadata = secondMetadata) {
        BasicText("duplicate-second-content")
      }

    val adapted = adaptAndroidNavDisplayEntries(listOf(first, second))

    assertEquals(2, adapted.backStack.size)
    val adaptedFirst = adapted.entriesByKey.getValue(adapted.backStack[0])
    val adaptedSecond = adapted.entriesByKey.getValue(adapted.backStack[1])
    assertSame(opaqueKey, adaptedFirst.contentKey)
    assertSame(opaqueKey, adaptedSecond.contentKey)
    assertSame(firstMetadata, adaptedFirst.metadata)
    assertSame(secondMetadata, adaptedSecond.metadata)

    composeRule.setContent {
      Column {
        adaptedFirst.Content()
        adaptedSecond.Content()
      }
    }
    composeRule.onNodeWithText("duplicate-first-content").assertIsDisplayed()
    composeRule.onNodeWithText("duplicate-second-content").assertIsDisplayed()
  }

  private data class OpaqueContentKey(val value: String)

  private data object FirstRoute : Route

  private data object SecondRoute : Route
}
