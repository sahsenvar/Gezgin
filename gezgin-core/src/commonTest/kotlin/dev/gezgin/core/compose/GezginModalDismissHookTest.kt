@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.compose

import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GezginModalDismissHookTest {

  private data class ConfirmDialog(val onDismiss: () -> Unit) : Route

  private fun nav() =
    RawNavigator(start = Feed, topology = testTopology).apply { navigate(Catalog) }

  private fun scope(withHook: Boolean) =
    GezginEntryScope().apply {
      register<ConfirmDialog>(
        kind = EntryKind.DIALOG,
        onDismiss = if (withHook) ({ route -> route.onDismiss() }) else null,
      ) {}
    }

  @Test
  fun dismissCallsTheHookAndThenPopsWhenTheHookLeftTheEntryOnStack() {
    val nav = nav()
    var dismissed = 0
    nav.open(ConfirmDialog { dismissed++ })

    gezginModalDismiss(nav, scope(withHook = true)).invoke(nav.currentEntryId)

    assertEquals(1, dismissed)
    assertEquals(Catalog, nav.current)
  }

  @Test
  fun dismissDoesNotPopAgainWhenTheHookAlreadyClosedTheModal() {
    val nav = nav()
    nav.open(ConfirmDialog { nav.back() })

    gezginModalDismiss(nav, scope(withHook = true)).invoke(nav.currentEntryId)

    assertEquals(listOf(Feed, Catalog), nav.backStack.value)
  }

  @Test
  fun dismissWithoutHookPopsTheModal() {
    val nav = nav()
    nav.open(ConfirmDialog {})

    gezginModalDismiss(nav, scope(withHook = false)).invoke(nav.currentEntryId)

    assertEquals(Catalog, nav.current)
  }

  @Test
  fun isOnStackTurnsFalseOnceTheEntryIsPopped() {
    val nav = nav()
    nav.open(ConfirmDialog {})
    val dialogId = nav.currentEntryId

    assertTrue(nav.isOnStack(dialogId))
    nav.back()
    assertFalse(nav.isOnStack(dialogId))
  }
}
