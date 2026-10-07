package dev.gezgin.core.compose

import androidx.compose.runtime.mutableStateOf
import dev.gezgin.core.GezginKey
import dev.gezgin.core.fixtures.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GezginWrapperScopeTest {
  private val graph = GezginGraph("AppGraph", GraphKind.Nav, emptyList(), null)

  private fun scope(
    keys: androidx.compose.runtime.State<List<GezginKey>>,
    entryId: Long,
    noBack: Boolean = false,
  ) =
    GezginWrapperScopeImpl(
      route = Product("1"),
      routeName = "Product",
      routeAnnotations = emptyList(),
      graph = graph,
      noBack = noBack,
      entryId = entryId,
      keys = keys,
    )

  @Test
  fun aLoneEntryIsAloneCannotGoBackAndIsTop() {
    val keys = mutableStateOf(listOf(GezginKey(Product("1"), id = 1)))
    val scope = scope(keys, entryId = 1)

    assertTrue(scope.isAloneInBackStack)
    assertFalse(scope.canGoBack)
    assertTrue(scope.isTop)
  }

  @Test
  fun anEntryUnderAnotherIsNotTopAndCanGoBack() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))

    val top = scope(keys, entryId = 2)
    val below = scope(keys, entryId = 1)

    assertTrue(top.isTop)
    assertTrue(top.canGoBack)
    assertFalse(top.isAloneInBackStack)
    assertFalse(below.isTop)
  }

  @Test
  fun aNoBackEntryCannotGoBackEvenWhenNotAlone() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))

    val scope = scope(keys, entryId = 2, noBack = true)

    assertFalse(scope.isAloneInBackStack)
    assertFalse(scope.canGoBack)
  }

  @Test
  fun stateFollowsTheStackWhenAnEntryIsPushedOverIt() {
    val keys = mutableStateOf(listOf(GezginKey(Product("0"), id = 1)))
    val scope = scope(keys, entryId = 1)
    assertTrue(scope.isTop)

    keys.value = keys.value + GezginKey(Product("1"), id = 2)

    assertFalse(scope.isTop)
    assertFalse(scope.isAloneInBackStack)
  }

  @Test
  fun anEntryThatLeftTheStackIsNotTop() {
    val keys = mutableStateOf(listOf(GezginKey(Product("0"), id = 1)))
    val scope = scope(keys, entryId = 99)

    assertFalse(scope.isTop)
  }

  @Test
  fun aPoppedEntryKeepsItsLastBackFlagsButIsNotTop() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))
    val popped = scope(keys, entryId = 2)
    assertTrue(popped.canGoBack)

    keys.value = keys.value.dropLast(1)

    assertFalse(popped.isAloneInBackStack)
    assertTrue(popped.canGoBack)
    assertFalse(popped.isTop)
  }

  @Test
  fun aPoppedNoBackEntryStillCannotGoBack() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))
    val popped = scope(keys, entryId = 2, noBack = true)
    assertFalse(popped.canGoBack)

    keys.value = keys.value.dropLast(1)

    assertFalse(popped.canGoBack)
    assertFalse(popped.isAloneInBackStack)
  }

  @Test
  fun aPoppedEntryReadForTheFirstTimeFallsBackToTheLiveStack() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))
    val popped = scope(keys, entryId = 2)

    keys.value = keys.value.dropLast(1)

    assertTrue(popped.isAloneInBackStack)
    assertFalse(popped.canGoBack)
    assertFalse(popped.isTop)
  }

  @Test
  fun graphParentChainIsReadableOutward() {
    val flow = GezginGraph("CheckoutFlow", GraphKind.Flow, emptyList(), graph)

    assertEquals(GraphKind.Flow, flow.kind)
    assertEquals("AppGraph", flow.parent?.name)
    assertNull(flow.parent?.parent)
  }
}
