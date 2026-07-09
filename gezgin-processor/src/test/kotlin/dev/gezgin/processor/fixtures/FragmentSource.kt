package dev.gezgin.processor.fixtures

/**
 * Task 6.1 fixtures for [dev.gezgin.processor.FragmentModelReaderTest] — brownfield Fragment interop
 * (§11/§11.1/§11.2).
 *
 * **Fragment stub strategy (documented decision, mirrors [HILT_STUBS]/[KOIN_STUBS]):** `gezgin-processor`
 * has NO compile dependency on `androidx.fragment` (its symbols are read as string FQNs, and per §11.2
 * never will). So [FRAGMENT_STUB] declares a MINIMAL local `abstract class Fragment` under the EXACT real
 * FQN package `androidx.fragment.app` — the same string-FQN-reading contract the processor uses in
 * production, exercised against a compile-time-present stub. A `@FragmentScreen`-annotated
 * `class X : Fragment()` then type-checks with a no-arg ctor exactly as a real Fragment subclass would.
 */

/** Minimal `androidx.fragment.app.Fragment` stub — see file KDoc. No-arg ctor, extendable. */
val FRAGMENT_STUB = """
    package androidx.fragment.app

    abstract class Fragment
""".trimIndent()

/**
 * Leaf routes for the Fragment fixtures, in a DIFFERENT package than the Fragments themselves — so the
 * dump proves `routePackageName` is read off the route DECLARATION (cross-module-safe), not the
 * Fragment's own package. `ArchivedRoute` is `@NoBack` (pins `noBack=true` read off the route decl).
 */
val FRAGMENT_ROUTES = """
    package dev.gezgin.fragroutes

    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.NoBack

    data class OrderChainRoute(val id: String = "x") : Route

    @NoBack
    data class ArchivedRoute(val id: String = "x") : Route
""".trimIndent()

/**
 * Two well-formed `@FragmentScreen` leaves (§11.1 example shape) — no-arg ctors, mandatory route arg.
 * `OrderChainFragment` → route `OrderChainRoute` (x=`OrderChain`, noBack=false); `ArchivedFragment` →
 * `@NoBack ArchivedRoute` (x=`Archived`, noBack=true). Both routes live in `dev.gezgin.fragroutes`.
 */
val FRAGMENT_SOURCE = """
    package dev.gezgin.fragui

    import androidx.fragment.app.Fragment
    import dev.gezgin.core.annotation.FragmentScreen
    import dev.gezgin.fragroutes.OrderChainRoute
    import dev.gezgin.fragroutes.ArchivedRoute

    @FragmentScreen(OrderChainRoute::class)
    class OrderChainFragment : Fragment()

    @FragmentScreen(ArchivedRoute::class)
    class ArchivedFragment : Fragment()
""".trimIndent()

/**
 * Fix-round fixture pinning the CONDITIONAL nav-wiring guard (the FS5 / SC2-MV7-parity fix). Compiled
 * ALONGSIDE [SHOP_SOURCE] so its two `@FragmentScreen` leaves target routes that ARE in this module's model
 * with a KNOWN navigator status (unlike [FRAGMENT_ROUTES], whose graph-less routes fall to the `?: true`
 * cross-module-optimistic fallback):
 * - `FeedFragment` → `HomeGraph.Feed`, which earns a `FeedNavigator` (has `@GoTo`/`@GoForResult` edges) →
 *   nav wiring MUST be emitted exactly as before (regression pin for the WITH-navigator case).
 * - `AboutFragment` → `HomeGraph.About`, [SHOP_SOURCE]'s deliberately-bare route (no edges/back-edges/
 *   result-contract) → earns NO navigator → nav wiring MUST be SUPPRESSED (no `val nav`, no `aboutNavigator`
 *   import, 2-arg `bindGezgin(fragment, route)`), the edge-less-leaf display-only case.
 */
val FRAGMENT_NAV_SPLIT_SOURCE = """
    package dev.gezgin.shopfrag

    import androidx.fragment.app.Fragment
    import dev.gezgin.core.annotation.FragmentScreen
    import dev.gezgin.shop.HomeGraph

    @FragmentScreen(HomeGraph.Feed::class)
    class FeedFragment : Fragment()

    @FragmentScreen(HomeGraph.About::class)
    class AboutFragment : Fragment()
""".trimIndent()
