package dev.gezgin.core
import dev.gezgin.core.fixtures.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class GezginKeyTest {
    private val json = Json { serializersModule = testSerializersModule }

    @Test fun roundTripsPolymorphicRouteWithIdentity() {
        val key = GezginKey(Product("42"), id = 7, flowPath = listOf(3, 5))
        val restored = json.decodeFromString<GezginKey>(json.encodeToString(GezginKey.serializer(), key))
        assertEquals(key, restored)
    }
    @Test fun equalRoutesWithDifferentIdsAreDistinctKeys() {
        assertNotEquals(GezginKey(Product("42"), 1), GezginKey(Product("42"), 2))
    }

    /**
     * Task 3.5 (Important 2) — §9 "getter zorunlu" kuralının ÇALIŞAN kanıtı: `transition` override'ı
     * GETTER'la yazılmış route'lar (`ScreenOwnTransition` kendi override'ı, `ScreenInheritsGraphTransition`
     * graph-mirası) backing field üretmez → kotlinx.serialization non-serializable `GezginTransition`
     * alanına takılmadan `GezginKey` round-trip'i tamamlar. (Initializer'lı hâli zaten DERLENMEZDİ —
     * bu test, serialization codegen'in getter'lı property'yi gerçekten yok saydığını runtime'da pinler.)
     */
    @Test fun roundTripsRoutesWithTransitionOverrides() {
        for (route in listOf(ScreenOwnTransition, ScreenInheritsGraphTransition, ScreenBackOnlyTransition)) {
            val key = GezginKey(route, id = 11)
            val restored = json.decodeFromString<GezginKey>(json.encodeToString(GezginKey.serializer(), key))
            assertEquals(key, restored, "round-trip kirildi: ${route::class.simpleName}")
        }
    }
}
