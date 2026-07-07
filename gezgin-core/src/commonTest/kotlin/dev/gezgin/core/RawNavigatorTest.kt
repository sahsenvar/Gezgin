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

    // (idempotent launch guard: bkz. explicitCallerGuardPreventsDoublePush — dedupe (caller, edge) başınadır)

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

    // (d) Re-entry into the same flow type via @GoForResult mints a NEW flow instance
    // (spec §8.1 re-entrancy boundary). Discriminates an implementation that resolves
    // enterFlow from the common-prefix rule (inheriting the outer instance id), which
    // would make quitWith tear down BOTH instances.
    @Test fun reentrantGoForResultMintsNewFlowInstance() = runTest {
        val n = nav()
        n.navigate(Catalog)
        n.launchForResult("Catalog→CheckoutFlow", Cart)      // dış instance
        n.navigate(Payment)
        n.launchForResult("Catalog→CheckoutFlow", Cart)      // İÇTEN re-entry (caller = Payment)
        n.quitWith(OrderId("inner"))                          // yalnız İÇ instance yıkılmalı
        assertEquals(Payment, n.current)                      // dış flow DURUYOR
        assertEquals(listOf<Route>(Feed, Catalog, Cart, Payment), n.backStack.value)
    }

    // (e) Re-launch while a delivered-but-unconsumed slot exists for the same (caller, edge)
    // must NOT push — the pre-guard must match bus.launch's predicate (ANY slot, not only
    // result == null). Discriminates a guard that only checks in-flight slots, which would
    // push an orphan entry with no slot attached.
    @Test fun relaunchWithUnconsumedResultDoesNotPushAgain() = runTest {
        val n = nav()
        n.navigate(Catalog)
        n.launchForResult("Catalog→CheckoutFlow", Cart)
        n.back()                                              // Canceled slota yazıldı, TÜKETİLMEDİ; caller yine top
        val sizeBefore = n.backStack.value.size
        n.launchForResult("Catalog→CheckoutFlow", Cart)       // slot hâlâ var → push YOK (guard)
        assertEquals(sizeBefore, n.backStack.value.size)
        assertEquals(NavResult.Canceled, n.results<OrderId>("Catalog→CheckoutFlow").first())
    }

    // --- Final-review fix tests ---

    @Test fun backToAcrossPendingTargetDeliversCanceledToSurvivingCaller() = runTest {
        val n = nav()   // start=Feed
        val callerId = n.currentEntryId
        val r = async { n.navigateForResult<OrderId>(callerId, "Catalog→CheckoutFlow", Cart) }
        runCurrent()
        n.navigate(Payment)
        n.backTo(Feed::class)                       // [Cart, Payment] kalkar; caller Feed hayatta
        assertEquals(NavResult.Canceled, r.await()) // slot LEAK yok, await sonsuza dek asılı kalmaz
    }

    @Test fun nestedQuitWithDoesNotDeliverValueToInnerSlot() = runTest {
        val n = nav()
        n.navigate(Catalog)
        val outerCaller = n.currentEntryId
        val outer = async { n.navigateForResult<OrderId>(outerCaller, "Catalog→CheckoutFlow", Cart) }
        runCurrent()
        n.navigate(Payment)
        val innerCaller = n.currentEntryId
        val inner = async { n.navigateForResult<OrderId>(innerCaller, "Feed→AddressPick", Otp) }
        runCurrent()
        n.quitWith(OrderId("done"))                 // dış flow biter
        assertEquals(NavResult.Value(OrderId("done")), outer.await())
        assertFalse(inner.isCompleted)              // iç slota YANLIŞ tipte Value teslim edilmedi
        inner.cancel()
    }

    @Test fun explicitCallerGuardPreventsDoublePush() = runTest {
        val n = nav()
        val callerId = n.currentEntryId
        n.launchForResult(callerId, "Catalog→CheckoutFlow", Cart)
        n.launchForResult(callerId, "Catalog→CheckoutFlow", Cart)   // AYNI explicit caller → guard
        assertEquals(2, n.backStack.value.size)
    }

    @Test fun launchForResultOnDuplicateTopStillCreatesSlotAndEntry() = runTest {
        val n = nav()
        n.navigate(Catalog); n.navigate(Cart)       // Cart zaten top (normal @GoTo ile)
        val callerId = n.currentEntryId
        val r = async { n.navigateForResult<OrderId>(callerId, "Catalog→CheckoutFlow", Cart) }
        runCurrent()
        assertEquals(4, n.backStack.value.size)     // singleTop=false: yeni Cart instance push edildi
        n.quitWith(OrderId("x"))
        assertEquals(NavResult.Value(OrderId("x")), r.await())      // asılı kalmıyor
    }

    @Test fun successfulBackToEmitsPoppedTo() = runTest {
        val n = nav()
        n.navigate(Catalog); n.navigate(Product("1"))
        val collected = mutableListOf<NavEvent>()
        val job = launch { n.events.collect { collected += it } }
        runCurrent()
        n.backTo(Catalog::class)                    // [Product("1")] kalkar
        runCurrent()
        val ev = collected.filterIsInstance<NavEvent.PoppedTo>().singleOrNull()
        assertNotNull(ev)
        assertEquals("Catalog", ev.target)
        assertEquals(listOf<Route>(Product("1")), ev.removed)
        assertEquals(listOf<Route>(Feed, Catalog), n.backStack.value)
        job.cancel()
    }

    // quitWith'in Value'su YALNIZ flow'un kendi entry slotuna gider; explicit OUT-OF-FLOW caller'lı
    // (caller = Feed, target = Otp — flow ile birlikte kalkan) yabancı-tipli slot Canceled alır.
    // Eski kod burada inner'a Value(OrderId) teslim ederdi → discriminator.
    @Test fun quitWithDeliversValueOnlyToFlowEntrySlot_othersCanceled() = runTest {
        val n = nav()
        val feedId = n.currentEntryId               // Feed — flow DIŞI explicit caller
        n.navigate(Catalog)
        val outerCaller = n.currentEntryId
        val outer = async { n.navigateForResult<OrderId>(outerCaller, "Catalog→CheckoutFlow", Cart) }
        runCurrent()
        n.navigate(Payment)
        val inner = async { n.navigateForResult<Pick>(feedId, "Feed→AddressPick", Otp) }
        runCurrent()
        n.quitWith(OrderId("d"))                    // CheckoutFlow biter: [Cart, Payment, Otp] kalkar
        assertEquals(NavResult.Value(OrderId("d")), outer.await())  // flow-entry (Cart) slotu → Value
        assertEquals(NavResult.Canceled, inner.await())             // Feed hayatta → Canceled, YANLIŞ-TİPLİ Value DEĞİL
    }

    // --- Task 2.5: @QuitAndGoTo runtime hook ---

    // (1) normal case: nested (non-root) flow is torn down (pending caller → Canceled,
    // FlowQuit(canceled=true) emitted), then the target is pushed on top of the surviving stack.
    @Test fun quitAndGoToTearsDownNestedFlowThenNavigates() = runTest {
        val n = nav()
        n.navigate(Catalog)
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        n.navigate(Payment)                                    // top = Payment, inside CheckoutFlow

        val eventsCollected = mutableListOf<NavEvent>()
        val job = launch { n.events.collect { eventsCollected += it } }
        runCurrent()

        n.quitAndGoTo(Product("p1"))

        assertEquals(NavResult.Canceled, r.await())            // flow teardown → pending caller Canceled
        assertEquals(Product("p1"), n.current)
        assertEquals(listOf<Route>(Feed, Catalog, Product("p1")), n.backStack.value)
        assertTrue(eventsCollected.any { it is NavEvent.FlowQuit && it.canceled })
        assertTrue(eventsCollected.any { it is NavEvent.Pushed && it.route == Product("p1") })
        job.cancel()
    }

    // (2) root-flow case: the flow being quit IS the root entry (quitFlow → null) → falls to
    // onRootBack/RootBack exactly like quit()/quitWith, and does NOT navigate (teardown itself failed).
    @Test fun quitAndGoToAtRootFlowFallsToOnRootBackAndDoesNotNavigate() = runTest {
        var rootBackCount = 0
        val n = RawNavigator(start = Cart, topology = testTopology, onRootBack = { rootBackCount++ })

        n.quitAndGoTo(Catalog)

        assertEquals(1, rootBackCount)
        assertEquals(Cart, n.current)                          // untouched — navigate never ran
        assertEquals(listOf<Route>(Cart), n.backStack.value)
    }
}
