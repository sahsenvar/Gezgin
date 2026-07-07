package dev.gezgin.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ResultBusTest {
    @Test
    fun secondLaunchOnSameEdgeIsNoOp() {
        val bus = ResultBus()
        assertTrue(bus.launch(callerEntryId = 1, edgeId = "e", targetEntryId = 9))
        assertFalse(bus.launch(1, "e", 10))                        // idempotent (§6)
        assertEquals(1, bus.slots.size)
    }

    @Test
    fun deliverThenLateCollectorReplaysOnce() = runTest {
        val bus = ResultBus()
        bus.launch(1, "e", 9)
        bus.deliver(9, NavResult.Value("adres"))                   // collector YOKKEN teslim (PD senaryosu)
        val r = bus.results<String>(1, "e").first()                // geç collector → replay
        assertEquals(NavResult.Value("adres"), r)
        assertTrue(bus.slots.isEmpty())                            // tüketildi
    }

    @Test
    fun liveCollectorGetsResultImmediately() = runTest {
        val bus = ResultBus()
        bus.launch(1, "e", 9)
        val deferred = async { bus.results<String>(1, "e").first() }
        runCurrent()
        bus.deliver(9, NavResult.Value("x"))
        assertEquals(NavResult.Value("x"), deferred.await())
    }

    @Test
    fun droppedCallersLoseSlots() {
        val bus = ResultBus()
        bus.launch(1, "e", 9)
        val dropped = bus.dropFor(setOf(1))
        assertEquals("e", dropped.single().edgeId)
        assertTrue(bus.slots.isEmpty())
    }

    // Additional test (a): canceled delivery replays
    @Test
    fun canceledDeliveryReplaysToLateCollector() = runTest {
        val bus = ResultBus()
        bus.launch(1, "e", 9)
        bus.deliver(9, NavResult.Canceled)                         // deliver Canceled result
        val r = bus.results<String>(1, "e").first()                // geç collector → replay
        assertEquals(NavResult.Canceled, r)
        assertTrue(bus.slots.isEmpty())                            // tüketildi
    }

    // Additional test (b): two different callers with same edgeId don't cross-talk
    @Test
    fun differentCallersWithSameEdgeIdDontCrossTalk() = runTest {
        val bus = ResultBus()
        bus.launch(callerEntryId = 1, edgeId = "e", targetEntryId = 9)
        bus.launch(callerEntryId = 2, edgeId = "e", targetEntryId = 10)
        assertEquals(2, bus.slots.size)

        bus.deliver(9, NavResult.Value("result1"))
        bus.deliver(10, NavResult.Value("result2"))

        val r1 = bus.results<String>(1, "e").first()
        assertEquals(NavResult.Value("result1"), r1)

        val r2 = bus.results<String>(2, "e").first()
        assertEquals(NavResult.Value("result2"), r2)

        assertTrue(bus.slots.isEmpty())                            // both tüketildi
    }

    // Additional test (c): restore round-trip
    @Test
    fun restoreRoundTrip() = runTest {
        val bus1 = ResultBus()
        bus1.launch(1, "e1", 9)
        bus1.launch(2, "e2", 10)
        bus1.deliver(9, NavResult.Value("restored1"))
        bus1.deliver(10, NavResult.Value("restored2"))

        val slotsCopy = bus1.slots
        assertEquals(2, slotsCopy.size)

        val bus2 = ResultBus()
        bus2.restore(slotsCopy)
        assertEquals(2, bus2.slots.size)

        val r1 = bus2.results<String>(1, "e1").first()
        assertEquals(NavResult.Value("restored1"), r1)

        val r2 = bus2.results<String>(2, "e2").first()
        assertEquals(NavResult.Value("restored2"), r2)

        assertTrue(bus2.slots.isEmpty())
    }
}
