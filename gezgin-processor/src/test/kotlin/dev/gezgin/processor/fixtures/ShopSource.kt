package dev.gezgin.processor.fixtures

/**
 * Minimal by-example fixture mirroring a Shopr-shaped navigation graph, used by [ModelReaderTest][
 * dev.gezgin.processor.ModelReaderTest]: a `@NavGraph` (`HomeGraph`) with a plain route, a route
 * with constructor params, and a `@GoForResult` edge into a `@FlowGraph` (`CheckoutFlow`) that is
 * itself a `ResultFlow<OrderId>` and nests a second `@FlowGraph` (`PayAuthFlow`).
 *
 * Kept as a raw string constant (rather than a `.kt` file on the test classpath) so
 * [dev.gezgin.processor.CompileHarness] can feed it to kctfork as an in-memory `SourceFile`.
 */
val SHOP_SOURCE = """
    package dev.gezgin.shop

    import dev.gezgin.core.ResultFlow
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.BackTo
    import dev.gezgin.core.annotation.BackToStart
    import dev.gezgin.core.annotation.FlowGraph
    import dev.gezgin.core.annotation.GoForResult
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.NoBack
    import dev.gezgin.core.annotation.Quit
    import dev.gezgin.core.annotation.StartDestination

    data class OrderId(val value: String)

    @NavGraph
    interface HomeGraph : Route {

        @GoTo(Product::class)
        @GoForResult(CheckoutFlow::class)
        data object Feed : HomeGraph

        data object Catalog : HomeGraph

        @NoBack
        @BackTo(Feed::class)
        data class Product(val id: String) : HomeGraph
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
    }
""".trimIndent()
