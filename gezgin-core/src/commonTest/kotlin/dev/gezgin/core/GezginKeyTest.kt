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
}
