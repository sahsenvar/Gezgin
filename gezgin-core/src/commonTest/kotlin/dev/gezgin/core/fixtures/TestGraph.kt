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

@Serializable data object Otp : ShopGraph       // PayAuthFlow üyesi (nested: Checkout > PayAuth)
@Serializable data object GiftPick : ShopGraph  // GiftFlow üyesi   (nested: Checkout > Gift)

val testSerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Feed::class); subclass(Catalog::class); subclass(Product::class)
        subclass(Cart::class); subclass(Payment::class)
    }
}

val checkoutFlow = dev.gezgin.core.FlowType("CheckoutFlow", isResultFlow = true)
val payAuthFlow = dev.gezgin.core.FlowType("PayAuthFlow", isResultFlow = false)
val giftFlow = dev.gezgin.core.FlowType("GiftFlow", isResultFlow = false)
val testTopology = dev.gezgin.core.GezginTopology(
    flowChains = mapOf(
        Cart::class to listOf(checkoutFlow), Payment::class to listOf(checkoutFlow),
        Otp::class to listOf(checkoutFlow, payAuthFlow),
        GiftPick::class to listOf(checkoutFlow, giftFlow),
    ),
    flowStarts = mapOf(
        "CheckoutFlow" to Cart::class,
        "PayAuthFlow" to Otp::class,
        "GiftFlow" to GiftPick::class,
    ),
    edges = mapOf("Catalog→CheckoutFlow" to dev.gezgin.core.EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer())),
)
