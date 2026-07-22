package dev.gezgin.core

import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

/**
 * Task 6.2 — pure-JVM (Android-framework-free) proof of the PD-safe SERIALIZATION half of
 * `dev.gezgin.core.fragment.Route.toBundle()` / `decodeGezginRoute()` (Task 6.0 §2c/§2-decision:
 * the genuinely risky half needs no `Bundle` and no Robolectric; the Bundle glue is Android-only
 * and compile-verified).
 *
 * `toBundle`/`decodeGezginRoute` encode a `Route` **polymorphically** via
 * `PolymorphicSerializer(Route::class)` using the app's `Json` (the SAME instance backing Gezgin's
 * backstack PD — reused, no second Json). This mirrors that exact mechanism against a test
 * `SerializersModule` (like [SavedStateTest]) to prove: any route that survives Gezgin PD survives
 * `toBundle()` by construction. A round-trip through the polymorphic serializer IS what happens
 * inside `Bundle.putString(KEY, encode(route))` → `getString(KEY)` → `decode(...)`.
 */
class FragmentRouteSerializationTest {

  private val json = Json { serializersModule = testSerializersModule }
  private val routeSerializer = PolymorphicSerializer(Route::class)

  /**
   * Encode → decode a route through the polymorphic serializer, exactly as
   * toBundle/decodeGezginRoute do.
   */
  private fun roundTrip(route: Route): Route =
    json.decodeFromString(routeSerializer, json.encodeToString(routeSerializer, route))

  @Test
  fun dataObjectRouteRoundTrips() {
    assertEquals(Feed, roundTrip(Feed))
    assertEquals(Catalog, roundTrip(Catalog))
  }

  @Test
  fun dataClassRouteWithPayloadRoundTrips() {
    val product = Product("sku-42")
    val decoded = roundTrip(product)
    assertEquals(product, decoded)
    // Concrete-type fidelity: polymorphic decode reconstructs the exact subtype, not a bare Route.
    assertEquals("sku-42", (decoded as Product).id)
  }

  @Test
  fun encodingIsPolymorphic_carriesTypeDiscriminatorSoDecodeCanPickTheSubtype() {
    // The encoded String must carry the polymorphic type tag (that's what lets a Fragment's
    // argsless
    // decode reconstruct the right route type from the app module) — the crux of PD-safety.
    val encoded = json.encodeToString(routeSerializer, Product("x"))
    assertEquals(true, encoded.contains("Product"), encoded)
  }
}
