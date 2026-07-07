package dev.gezgin.processor.fixtures

/**
 * Minimal by-example fixture mirroring a Shopr-shaped navigation graph, used by [ModelReaderTest][
 * dev.gezgin.processor.ModelReaderTest]: a `@NavGraph` (`HomeGraph`) with a plain route, a route
 * with constructor params, and a `@GoForResult` edge into a `@FlowGraph` (`CheckoutFlow`) that is
 * itself a `ResultFlow<OrderId>` and nests a second `@FlowGraph` (`PayAuthFlow`).
 *
 * Also pins the trickier reading rules: `@Repeatable` flattening (`Deals`), the `Self::class`
 * sentinel and explicit args on `@ReplaceTo` (`Promo`), result types via an intermediate base
 * class (`BasePicker`/`AddressPicker`), nullable+defaulted ctor params (`AddressPicker`), and flow
 * chains skipping an intervening `@NavGraph` (`CheckoutPages`/`GiftFlow`).
 *
 * Kept as a raw string constant (rather than a `.kt` file on the test classpath) so
 * [dev.gezgin.processor.CompileHarness] can feed it to kctfork as an in-memory `SourceFile`.
 *
 * `OrderId` carries a hand-written `companion object { fun serializer(): KSerializer<OrderId> }`
 * stub rather than `@Serializable` — Task 2.4's codegen (`TopologyCodegen`) always emits a real
 * `OrderId.serializer()` call for the `Feed`→`CheckoutFlow` `@GoForResult` edge (real application
 * code has the kotlinx-serialization compiler plugin and a genuine `@Serializable`-generated
 * `.serializer()`), but kctfork's test compilation has no such plugin wired in.
 *
 * The stub's `serializer()` factory itself must succeed — `gezginTopology` is a top-level `val`,
 * so its initializer (including this call) runs eagerly in `GezginGeneratedKt.<clinit>` the moment
 * the generated file is classloaded, which every test that reads `gezginTopology` does. What must
 * never happen is a real (de)serialize call, so the returned [KSerializer] is a real object whose
 * factory construction is cheap and side-effect-free, but whose actual serialization methods throw
 * if a test ever mistakenly exercises them.
 */
val SHOP_SOURCE = """
    package dev.gezgin.shop

    import dev.gezgin.core.ResultFlow
    import dev.gezgin.core.ResultRoute
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.BackTo
    import dev.gezgin.core.annotation.BackToStart
    import dev.gezgin.core.annotation.FlowGraph
    import dev.gezgin.core.annotation.GoForResult
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.NoBack
    import dev.gezgin.core.annotation.Quit
    import dev.gezgin.core.annotation.ReplaceTo
    import dev.gezgin.core.annotation.StartDestination
    import kotlinx.serialization.KSerializer
    import kotlinx.serialization.descriptors.SerialDescriptor
    import kotlinx.serialization.descriptors.buildClassSerialDescriptor
    import kotlinx.serialization.encoding.Decoder
    import kotlinx.serialization.encoding.Encoder

    data class OrderId(val value: String) {
        // Test-only stub — see the file-level KDoc above. Never actually (de)serializes.
        companion object {
            fun serializer(): KSerializer<OrderId> = object : KSerializer<OrderId> {
                override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OrderId")
                override fun serialize(encoder: Encoder, value: OrderId): Unit =
                    throw UnsupportedOperationException("test stub — no kotlinx-serialization plugin in kctfork")
                override fun deserialize(decoder: Decoder): OrderId =
                    throw UnsupportedOperationException("test stub — no kotlinx-serialization plugin in kctfork")
            }
        }
    }

    // Intermediate non-route layer (top-level, not nested in a graph) implementing ResultRoute —
    // exercises transitive supertype result-type resolution.
    abstract class BasePicker : ResultRoute<OrderId>

    @NavGraph
    interface HomeGraph : Route {

        @GoTo(Product::class)
        @GoForResult(CheckoutFlow::class)
        data object Feed : HomeGraph

        // Screen-mode @GoForResult (target = ResultRoute route, not a flow) with a name= override —
        // exercises the named-@GoForResult X-substitution across Task 2.5's generated triple
        // (launchPickAddress / pickAddressResults / goToPickAddressForResult).
        @GoForResult(AddressPicker::class, name = "pickAddress")
        data object Catalog : HomeGraph

        // Deliberately bare: no edges, no back-annotations, no result contract, no ResultFlow
        // membership — pins that Task 2.5 generates NO navigator for such a route.
        data object About : HomeGraph

        @NoBack
        @BackTo(Feed::class)
        data class Product(val id: String) : HomeGraph

        // Two @GoTo's to distinct targets — exercises @Repeatable flattening.
        @GoTo(Catalog::class)
        @GoTo(Product::class)
        data object Deals : HomeGraph

        // Default clearUpTo (Self::class sentinel) vs fully explicit @ReplaceTo args.
        @ReplaceTo(Catalog::class)
        @ReplaceTo(Product::class, clearUpTo = Feed::class, inclusive = false, name = "viaFeed")
        data object Promo : HomeGraph

        // ResultRoute via intermediate base class + nullable, defaulted ctor param + a GENERIC
        // ctor param (`List<String>`) — pins that navigator codegen forwards the full parameterized
        // type (`List<String>`), not its raw erasure (`List`), so the generated method compiles.
        class AddressPicker(val hint: String? = null, val tags: List<String> = emptyList()) : BasePicker(), HomeGraph
    }

    @FlowGraph
    interface CheckoutFlow : Route, ResultFlow<OrderId> {

        @StartDestination
        @GoTo(Payment::class)
        data object Cart : CheckoutFlow

        @Quit
        @BackToStart
        data object Payment : CheckoutFlow

        @FlowGraph
        interface PayAuthFlow : Route {

            @StartDestination
            data object Otp : PayAuthFlow
        }

        // Intervening @NavGraph between two @FlowGraph's — flow chains must skip it.
        // NOTE: extends Route, NOT CheckoutFlow — extending the enclosing flow would transitively
        // inherit its ResultFlow<OrderId> supertype and (correctly) mark this graph resultFlow=true.
        @NavGraph
        interface CheckoutPages : Route {

            @FlowGraph
            interface GiftFlow : Route {

                @StartDestination
                data object GiftPick : GiftFlow
            }
        }
    }
""".trimIndent()
