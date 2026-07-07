package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SavedStateTest {
    private val json = Json { serializersModule = testSerializersModule }

    // --- Brief's 2 verbatim tests ---

    @Test fun pendingDeliveredResultSurvivesProcessDeath() = kotlinx.coroutines.test.runTest {
        val n1 = RawNavigator(Feed, testTopology)
        n1.navigate(Catalog)
        n1.launchForResult("Catalog→CheckoutFlow", Cart)
        n1.backWithResult(OrderId("o1"))                  // sonuç teslim, TÜKETİCİ YOK (continuation "öldü")
        val saved = json.encodeToString(SavedState.serializer(), n1.save(json))

        val n2 = RawNavigator(Feed, testTopology, restored = json.decodeFromString(SavedState.serializer(), saved))
        assertEquals(listOf(Feed, Catalog), n2.backStack.value)               // stack restore
        val r = n2.results<OrderId>("Catalog→CheckoutFlow").first()           // geç re-attach (VM init)
        assertEquals(NavResult.Value(OrderId("o1")), r)
    }

    @Test fun nextIdSurvives_noIdCollisionAfterRestore() = kotlinx.coroutines.test.runTest {
        val n1 = RawNavigator(Feed, testTopology); n1.navigate(Catalog)
        val saved = n1.save(json)
        val n2 = RawNavigator(Feed, testTopology, restored = saved)
        n2.navigate(Product("z"))
        assertEquals(n2.keys.map { it.id }.toSet().size, n2.keys.size)        // benzersiz id'ler
    }

    // --- Additional required coverage: in-flight slot round-trip, late collector on restored navigator ---

    @Test fun inFlightSlotRoundTrips_lateBackWithResultDeliversAfterRestore() = runTest {
        val n1 = RawNavigator(Feed, testTopology)
        n1.navigate(Catalog)
        n1.launchForResult("Catalog→CheckoutFlow", Cart)   // in-flight: no delivery yet
        val saved = json.decodeFromString(SavedState.serializer(), json.encodeToString(SavedState.serializer(), n1.save(json)))

        val n2 = RawNavigator(Feed, testTopology, restored = saved)
        assertEquals(listOf(Feed, Catalog, Cart), n2.backStack.value)

        n2.backWithResult(OrderId("late"))                 // top (Cart) is still the restored pending target
        assertEquals(listOf(Feed, Catalog), n2.backStack.value)
        val r = n2.results<OrderId>("Catalog→CheckoutFlow").first()   // late re-attach after restore + delivery
        assertEquals(NavResult.Value(OrderId("late")), r)
    }

    // --- Additional required coverage: Canceled slot round-trips ---

    @Test fun canceledSlotRoundTrips_lateCollectorReceivesCanceled() = runTest {
        val n1 = RawNavigator(Feed, testTopology)
        n1.navigate(Catalog)
        n1.launchForResult("Catalog→CheckoutFlow", Cart)
        n1.back()                                          // flow entry back = Canceled delivery
        val saved = json.decodeFromString(SavedState.serializer(), json.encodeToString(SavedState.serializer(), n1.save(json)))

        val n2 = RawNavigator(Feed, testTopology, restored = saved)
        assertEquals(listOf(Feed, Catalog), n2.backStack.value)
        val r = n2.results<OrderId>("Catalog→CheckoutFlow").first()
        assertEquals(NavResult.Canceled, r)
    }
}
