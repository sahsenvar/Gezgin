package dev.gezgin.core.fixtures
import dev.gezgin.core.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ShopGraph : Route
@Serializable data object Feed : ShopGraph
@Serializable data object Catalog : ShopGraph
@Serializable data class Product(val id: String) : ShopGraph

@Serializable sealed interface CheckoutFlow : ShopGraph   // fixture: ResultFlow<OrderId> temsili
@Serializable data object Cart : CheckoutFlow
@Serializable data object Payment : CheckoutFlow
@Serializable data class OrderId(val v: String)

val testSerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Feed::class); subclass(Catalog::class); subclass(Product::class)
        subclass(Cart::class); subclass(Payment::class)
    }
}

val checkoutFlow = dev.gezgin.core.FlowType("CheckoutFlow", isResultFlow = true)
val testTopology = dev.gezgin.core.GezginTopology(
    flowChains = mapOf(Cart::class to listOf(checkoutFlow), Payment::class to listOf(checkoutFlow)),
    flowStarts = mapOf("CheckoutFlow" to Cart::class),
    edges = mapOf("Catalog→CheckoutFlow" to dev.gezgin.core.EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer())),
)
