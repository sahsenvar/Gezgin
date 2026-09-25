@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*
import kotlinx.serialization.json.Json

class CallbackModalNavigatorTest {
  private val snapshotJson = Json { serializersModule = testSerializersModule }

  private data class ConfirmDialog(val id: String, val onConfirm: () -> Unit) : Route

  private val topology =
    GezginTopology(
      flowChains = emptyMap(),
      flowStarts = emptyMap(),
      edges = mapOf("Confirm→Product" to EdgeSpec("Confirm→Product", resultSerializer = null)),
      transientRoutes = setOf(ConfirmDialog::class),
    )

  private fun nav() = RawNavigator(start = Feed, topology = topology)

  private fun RawNavigator.saveAndRestore(): RawNavigator {
    val encoded = snapshotJson.encodeToString(SavedState.serializer(), save())
    return RawNavigator(
      Feed,
      topology,
      onRootBack = {},
      json = snapshotJson,
      restored = snapshotJson.decodeFromString(SavedState.serializer(), encoded),
    )
  }

  @Test
  fun openPushesTheRouteInstanceCarryingTheCallerLambdas() {
    var confirmed = 0
    val n = nav()

    n.open(ConfirmDialog("a") { confirmed++ })
    (n.current as ConfirmDialog).onConfirm()

    assertEquals(1, confirmed)
    assertEquals(2, n.backStack.value.size)
  }

  @Test
  fun openIgnoresSecondOpenOfSameTypeWhileItIsOnTop() {
    val n = nav()

    n.open(ConfirmDialog("a") {})
    n.open(ConfirmDialog("a") {})

    assertEquals(2, n.backStack.value.size)
  }

  @Test
  fun openPushesSameTypeAgainWhenAnotherEntryIsOnTop() {
    val n = nav()

    n.open(ConfirmDialog("a") {})
    n.navigate(Product("p"))
    n.open(ConfirmDialog("a") {})

    assertEquals(4, n.backStack.value.size)
  }

  @Test
  fun saveDropsTransientTopEntrySoRestoreShowsTheScreenBehindIt() {
    val n = nav()
    n.navigate(Catalog)
    n.open(ConfirmDialog("a") {})

    val restored = n.saveAndRestore()

    assertEquals(listOf(Feed, Catalog), restored.backStack.value)
  }

  @Test
  fun saveDropsTransientMiddleEntryAndKeepsEntriesAboveIt() {
    val n = nav()
    n.open(ConfirmDialog("a") {})
    n.navigate(Product("p"))

    val restored = n.saveAndRestore()

    assertEquals(listOf(Feed, Product("p")), restored.backStack.value)
  }

  @Test
  fun dropTransientEntriesReleasesCallbackModalsAndKeepsOtherEntries() {
    val n = nav()
    n.open(ConfirmDialog("a") {})
    n.navigate(Product("p"))
    n.open(ConfirmDialog("b") {})

    n.dropTransientEntries()

    assertEquals(listOf(Feed, Product("p")), n.backStack.value)
  }

  @Test
  fun saveDropsResultSlotsWhoseCallerIsATransientEntry() {
    val n = nav()
    n.open(ConfirmDialog("a") {})
    n.launchForResult("Confirm→Product", Product("p"))

    assertTrue(n.save().pendingSlots.isEmpty())
  }
}
