package dev.gezgin.core.fixtures
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import dev.gezgin.core.Route
import dev.gezgin.core.compose.GezginTransition
import dev.gezgin.core.compose.transition
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

interface Pick                                                     // AÇIK polimorfizm — module ŞART
@Serializable data class ChosenAddress(val id: String) : Pick

// --- Task 3.5 (§9 transition cascade) fixture'ları — üç ayırt edilebilir GezginTransition örneği ile
// route-override > graph-default > app-default > null zincirinin HER basamağı ayrı bir route'la sınanır.
val graphTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }
val screenTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }
val appTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }

/** Graph-seviyesi transition override'ı (§9 "app/graph seviyesi = ağaç boyunca devralınan değer"). */
@Serializable
sealed interface TransitionGraph : Route {
    override val transition: GezginTransition? get() = graphTransitionFixture
}

/** Kendi override'ı YOK → [TransitionGraph]'ın graph-seviyesi değerini interface override zinciriyle miras alır. */
@Serializable data object ScreenInheritsGraphTransition : TransitionGraph

/** Kendi `transition` override'ı VAR → graph-seviyesini ezer (screen > graph, §9). */
@Serializable
data object ScreenOwnTransition : TransitionGraph {
    override val transition: GezginTransition? get() = screenTransitionFixture
}

/** Ne kendi ne de graph (`ShopGraph` override etmiyor) bir şey söylemiyor → app-seviyesine/`null`'a düşer. */
@Serializable data object ScreenNoTransitionAnywhere : ShopGraph

val testSerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Feed::class); subclass(Catalog::class); subclass(Product::class)
        subclass(Cart::class); subclass(Payment::class)
    }
    polymorphic(Pick::class) { subclass(ChosenAddress::class) }
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
    edges = mapOf(
        "Catalog→CheckoutFlow" to dev.gezgin.core.EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer()),
        "Feed→AddressPick" to dev.gezgin.core.EdgeSpec("Feed→AddressPick", kotlinx.serialization.PolymorphicSerializer(Pick::class)),
    ),
)
