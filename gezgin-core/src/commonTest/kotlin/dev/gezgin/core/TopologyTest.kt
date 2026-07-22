package dev.gezgin.core

import dev.gezgin.core.fixtures.Cart
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopologyTest {
  @Test
  fun flowChainOfFlowMemberContainsCheckout() {
    // M2 — FlowType is no longer a data class; compare by its public (id, isResultFlow) fields.
    assertEquals(
      listOf("CheckoutFlow" to true),
      testTopology.flowChain(Cart::class).map { it.id to it.isResultFlow },
    )
  }

  @Test
  fun flowChainOfPlainRouteIsEmpty() {
    assertTrue(testTopology.flowChain(Feed::class).isEmpty())
  }

  @Test
  fun startOfCheckoutIsCart() {
    assertEquals(Cart::class, testTopology.startOf("CheckoutFlow"))
  }
}
