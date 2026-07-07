package dev.gezgin.core

import dev.gezgin.core.fixtures.Cart
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopologyTest {
    @Test fun flowChainOfFlowMemberContainsCheckout() {
        assertEquals(listOf(FlowType("CheckoutFlow", isResultFlow = true)),
            testTopology.flowChain(Cart::class))
    }
    @Test fun flowChainOfPlainRouteIsEmpty() {
        assertTrue(testTopology.flowChain(Feed::class).isEmpty())
    }
    @Test fun startOfCheckoutIsCart() {
        assertEquals(Cart::class, testTopology.startOf("CheckoutFlow"))
    }
}
