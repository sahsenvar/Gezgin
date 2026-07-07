package dev.gezgin.test

import dev.gezgin.core.NavResult
import dev.gezgin.test.fixtures.Cart
import dev.gezgin.test.fixtures.Catalog
import dev.gezgin.test.fixtures.OrderId
import dev.gezgin.test.fixtures.Payment
import dev.gezgin.test.fixtures.Product
import dev.gezgin.test.fixtures.testTopology
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GezginTestNavigatorTest {

    // §13 by-example — brief verbatim.
    @Test fun replaceToClearsPaymentFlow() {
        val nav = GezginTestNavigator(start = Payment, topology = testTopology)
        nav.replaceTo(Product("order1"))
        assertEquals(Product("order1"), nav.current)
        assertEquals(1, nav.backStack.size)
    }

    // §13 by-example — brief verbatim.
    @Test fun checkoutReturnsSelectedResult() = runTest {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        val r = async { nav.raw.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        nav.deliverResult(OrderId("o1"))
        assertEquals(NavResult.Value(OrderId("o1")), r.await())
    }

    // Extra (a): back() at root invokes onRootBack — constructor param plumbed through.
    @Test fun backAtRootInvokesOnRootBack() {
        var rootBackCalled = false
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology, onRootBack = { rootBackCalled = true })
        nav.back()
        assertEquals(true, rootBackCalled)
    }

    // Extra (b): navigate + backStack snapshot — list grows, current updates.
    @Test fun navigateGrowsBackStackAndUpdatesCurrent() {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        assertEquals(1, nav.backStack.size)
        nav.navigate(Product("p1"))
        assertEquals(2, nav.backStack.size)
        assertEquals(Product("p1"), nav.current)
        assertEquals(listOf(Catalog, Product("p1")), nav.backStack)
    }
}
