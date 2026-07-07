package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class RawNavigatorTest {
    private fun nav(onRootBack: () -> Unit = {}) =
        RawNavigator(start = Feed, topology = testTopology, onRootBack = onRootBack)

    // --- Brief's 4 verbatim tests ---

    @Test fun goForResultRoundTrip() = runTest {
        val n = nav(); n.navigate(Catalog)
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        assertEquals(Payment::class, run { n.navigate(Payment); n.current::class })
        n.quitWith(OrderId("o1"))                                   // atomik teardown + deliver
        assertEquals(NavResult.Value(OrderId("o1")), r.await())
        assertEquals(Catalog, n.current)                            // flow yıkıldı, caller top
    }

    @Test fun plainBackOnPendingTargetDeliversCanceled() = runTest {
        val n = nav()
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent(); n.back()                                      // flow entry'de back = quit = Canceled (§8.1)
        assertEquals(NavResult.Canceled, r.await())
    }

    @Test fun backAtRootInvokesOnRootBack_notEmpty() {
        var root = 0
        val n = nav { root++ }
        n.back()
        assertEquals(1, root); assertEquals(Feed, n.current)
    }

    @Test fun duplicateLaunchDoesNotDoublePush() {
        val n = nav()
        n.launchForResult("Catalog→CheckoutFlow", Cart)
        n.launchForResult("Catalog→CheckoutFlow", Cart)             // idempotent
        assertEquals(2, n.backStack.value.size)                     // [Feed, Cart] — tek push
    }

    // --- Additional discriminating tests ---

    // (a) quit() on a non-root (nested) flow: delivers Canceled to the pending target
    // and emits FlowQuit(canceled = true). Discriminates an implementation that forgets
    // to check pending-target slots inside quit()'s removed-entries loop, or that emits
    // FlowQuit with canceled = false, or that emits Popped instead of FlowQuit.
    @Test fun quitOnNestedFlowDeliversCanceledAndEmitsFlowQuit() = runTest {
        val n = nav()
        n.navigate(Catalog)
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        n.navigate(Payment)                                          // top is now Payment, not the pending target itself

        val eventDeferred = async { n.events.first() }
        runCurrent()
        n.quit()

        assertEquals(NavResult.Canceled, r.await())
        assertEquals(Catalog, n.current)
        val ev = eventDeferred.await()
        assertTrue(ev is NavEvent.FlowQuit)
        assertTrue((ev as NavEvent.FlowQuit).canceled)
    }

    // (b) backTo with a missing target: emits BackToTargetMissing, does NOT mutate the
    // stack, and does NOT deliver any result. Discriminates an implementation that
    // silently no-ops without emitting the event, or that mutates the stack anyway.
    @Test fun backToMissingTargetEmitsEventWithNoMutation() = runTest {
        val n = nav()
        n.navigate(Catalog)
        val before = n.backStack.value

        val eventDeferred = async { n.events.first() }
        runCurrent()
        n.backTo(Payment::class)                                     // Payment was never pushed

        val ev = eventDeferred.await()
        assertTrue(ev is NavEvent.BackToTargetMissing)
        assertEquals("Payment", (ev as NavEvent.BackToTargetMissing).target)
        assertEquals(before, n.backStack.value)                       // stack untouched
        assertEquals(Catalog, n.current)
    }

    // (c) replaceTo that clears a caller's entry off the stack drops its pending slot
    // and emits ResultDropped. Discriminates an implementation that only refreshes
    // backStack/emits Replaced but forgets the dropFor(removed callers) step.
    @Test fun replaceToDropsCallersPendingSlot() = runTest {
        val n = nav()
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()                                                  // caller = Feed (top at call time)

        val collected = mutableListOf<NavEvent>()
        val job = launch { n.events.collect { collected += it } }
        runCurrent()

        n.replaceTo(Catalog, clearUpTo = Feed::class, inclusive = true) // clears Feed (the caller) + Cart (the target)
        runCurrent()

        assertTrue(collected.any { it is NavEvent.ResultDropped && it.edgeId == "Catalog→CheckoutFlow" })
        assertEquals(Catalog, n.current)
        r.cancel()
        job.cancel()
    }
}
