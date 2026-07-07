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

    data class OrderId(val value: String)

    // Intermediate non-route layer (top-level, not nested in a graph) implementing ResultRoute —
    // exercises transitive supertype result-type resolution.
    abstract class BasePicker : ResultRoute<OrderId>

    @NavGraph
    interface HomeGraph : Route {

        @GoTo(Product::class)
        @GoForResult(CheckoutFlow::class)
        data object Feed : HomeGraph

        data object Catalog : HomeGraph

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

        // ResultRoute via intermediate base class + nullable, defaulted ctor param.
        class AddressPicker(val hint: String? = null) : BasePicker(), HomeGraph
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
