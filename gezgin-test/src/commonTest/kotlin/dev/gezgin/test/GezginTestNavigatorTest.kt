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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    // Task 2.6: entryIdOf — nearest match, and the explanatory error when there is none.
    @Test fun entryIdOfReturnsNearestEntryId() {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        nav.navigate(Product("p1"))
        nav.navigate(Product("p2"))
        assertEquals(nav.raw.currentEntryId, nav.entryIdOf(Product::class))
    }

    @Test fun entryIdOfThrowsExplanatoryErrorWhenNoMatch() {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        val ex = assertFailsWith<IllegalStateException> { nav.entryIdOf(Payment::class) }
        assertTrue(ex.message.orEmpty().contains("Payment"))
    }

    // Faz 8.1 — the fixture's `CheckoutFlow` is FLAT-FILE-shaped: a top-level flow FQ ("CheckoutFlow")
    // with members declared `: CheckoutFlow` (not lexically nested). Runtime flow-unit behaviour keys
    // only on the flowChain/flowPath, so entering it mints a flow instance and `quit()` unwinds the
    // whole flow atomically back to the caller — identical to a nested flow.
    @Test fun flatFileFlowQuitUnwindsWholeFlow() {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        nav.navigate(Cart)       // enter the flat-file flow → fresh flowPath minted
        nav.navigate(Payment)    // deeper in the same flow instance (inherits the flowId)
        assertEquals(listOf(Catalog, Cart, Payment), nav.backStack)
        nav.raw.quit()           // quitFlow: atomic pop of the entire CheckoutFlow block
        assertEquals(listOf(Catalog), nav.backStack)
        assertEquals(Catalog, nav.current)
    }

    // Faz 8.1 — `quitWith` on the flat-file ResultFlow atomically unwinds the whole flow AND delivers
    // Value to the awaiting caller, proving the flowPath round-trip holds for a top-level flow FQ.
    @Test fun flatFileResultFlowQuitWithDeliversValueAndUnwinds() = runTest {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        val r = async { nav.raw.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        nav.navigate(Payment)                 // move deeper inside the flow before quitting
        assertEquals(listOf(Catalog, Cart, Payment), nav.backStack)
        nav.raw.quitWith(OrderId("done"))     // quitFlow with result → atomic pop + Value to caller
        assertEquals(NavResult.Value(OrderId("done")), r.await())
        assertEquals(listOf(Catalog), nav.backStack)
        assertEquals(Catalog, nav.current)
    }
}
