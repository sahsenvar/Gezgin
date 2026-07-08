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

// --- Task 4.1 (§7 modal contract) fixture'ları ---
/** Contract implement ETMEYEN dialog route → adapter tip-varsayılan DialogProperties (hepsi true). */
@Serializable data class DialogDefault(val id: String) : ShopGraph

/** DialogContract'ı ctor-param + override ile besleyen dialog route (KOŞULLU + SABİT karışık). */
@Serializable
data class DialogCustom(val id: String) : ShopGraph, dev.gezgin.core.DialogContract {
    override val dismissOnClickOutside: Boolean get() = false
    override val dismissOnBackPress: Boolean get() = false
    override val usePlatformDefaultWidth: Boolean get() = false
}

/** @NoBack + dismissOnBackPress=true (default) çelişkisi guard testi için dialog route. */
@Serializable data object DialogBackDismissable : ShopGraph, dev.gezgin.core.DialogContract

/** @NoBack ile UYUMLU dialog route: dismissOnBackPress=false → guard geçer. */
@Serializable
data object DialogNoBackCompatible : ShopGraph, dev.gezgin.core.DialogContract {
    override val dismissOnBackPress: Boolean get() = false
}

/** dismissOnBackPress KOŞULLU (ctor-param'dan) — guard'ın property GETTER'ından (sabit-override değil,
 *  instance-değerinden) okuduğunu pinlemek için (Task 4.1 Minor). */
@Serializable
data class ConditionalBackDialog(val backDismiss: Boolean) : ShopGraph, dev.gezgin.core.DialogContract {
    override val dismissOnBackPress: Boolean get() = backDismiss
}

/** FullscreenModalContract'lı tam-ekran modal route. */
@Serializable
data object FullModal : ShopGraph, dev.gezgin.core.FullscreenModalContract {
    override val dismissOnClickOutside: Boolean get() = false
}

// --- Task 4.2 (§7 BottomSheet) fixture'ları ---
/** Contract implement ETMEYEN sheet route → adapter tip-varsayılan props (skipPartiallyExpanded=false,
 *  dismissOnBackPress=true, dismissOnClickOutside=true). */
@Serializable data class SheetDefault(val id: String) : ShopGraph

/** BottomSheetContract'lı sheet route: üçünü de ezer (skipPartiallyExpanded=true, dismissOnBackPress=false,
 *  dismissOnClickOutside=false). */
@Serializable
data class SheetCustom(val id: String) : ShopGraph, dev.gezgin.core.BottomSheetContract {
    override val skipPartiallyExpanded: Boolean get() = true
    override val dismissOnBackPress: Boolean get() = false
    override val dismissOnClickOutside: Boolean get() = false
}

/** @NoBack + dismissOnBackPress=true (default) çelişkisi guard testi için sheet route. */
@Serializable data object SheetBackDismissable : ShopGraph, dev.gezgin.core.BottomSheetContract

/** @NoBack + BOTTOM_SHEET route — dismissOnBackPress=false OLSA BİLE artık YASAK (§7: swipe-to-dismiss
 *  hiçbir prop'la kapatılamaz → görsel/state desync). Guard'ın kind==BOTTOM_SHEET dalının dismiss'ten
 *  BAĞIMSIZ fırlattığını pinlemek için (eskiden "legal" varsayılıyordu — Faz4 final-review'da yasaklandı). */
@Serializable
data object SheetNoBackCompatible : ShopGraph, dev.gezgin.core.BottomSheetContract {
    override val dismissOnBackPress: Boolean get() = false
}

interface Pick                                                     // AÇIK polimorfizm — module ŞART
@Serializable data class ChosenAddress(val id: String) : Pick

// --- Task 3.5 (§9 transition cascade) fixture'ları — üç ayırt edilebilir GezginTransition örneği ile
// route-override > graph-default > app-default > null zincirinin HER basamağı ayrı bir route'la sınanır.
val graphTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }
val screenTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }
val appTransitionFixture: GezginTransition = transition { forward { fadeIn() togetherWith fadeOut() } }
/** YALNIZ `backward` set — forward yok, predictive yok (predictive→backward fallback'i metadata testinde sınanır). */
val backOnlyTransitionFixture: GezginTransition = transition { backward { fadeIn() togetherWith fadeOut() } }

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

/** YALNIZ `backward{}` set etmiş route — metadata testinde (pop key VAR / forward key YOK) B rolü. */
@Serializable
data object ScreenBackOnlyTransition : ShopGraph {
    override val transition: GezginTransition? get() = backOnlyTransitionFixture
}

val testSerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Feed::class); subclass(Catalog::class); subclass(Product::class)
        subclass(Cart::class); subclass(Payment::class)
        // Task 3.5 (Important 2): transition-override'lı route'lar da polimorfik kayıtta — GezginKey
        // round-trip'i getter'ın backing field ÜRETMEDİĞİNİN (serialization'a takılmadığının) çalışan kanıtı.
        subclass(ScreenOwnTransition::class); subclass(ScreenInheritsGraphTransition::class)
        subclass(ScreenBackOnlyTransition::class)
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
