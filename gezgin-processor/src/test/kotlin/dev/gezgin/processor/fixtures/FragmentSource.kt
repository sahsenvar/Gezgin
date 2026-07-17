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
 * Stand-ins for the `XNavigator` classes [dev.gezgin.processor.codegen.NavigatorCodegen] would generate in
 * the ROUTES' OWN module. In a real multi-module build that module compiles first, so its navigators are
 * already-compiled classes on the Fragment module's classpath; the cross-module nav-wiring PROBE
 * ([dev.gezgin.processor.codegen.NavigatorProbe]) then resolves them BY NAME and verifies IDENTITY via the
 * `@GezginNavigatorFor` marker before emitting nav wiring. The single-compilation kctfork harness has no
 * separate module, so these hand-written source classes model that "already compiled elsewhere" navigator —
 * they MUST carry the same `@GezginNavigatorFor(route)` stamp NavigatorCodegen emits, else the identity check
 * (FS5/M1) rejects them as name-only decoys. Paired with [FRAGMENT_ROUTES]/[FRAGMENT_SOURCE] (routes
 * `OrderChain`/`Archived`) this exercises the cross-module WITH-navigator branch deterministically (replacing
 * the old `?: true` optimism, which nav-wired blindly whether or not a navigator existed).
 */
val FRAGMENT_ROUTE_NAVIGATOR_STUBS = """
    package dev.gezgin.fragroutes

    import dev.gezgin.core.annotation.GezginNavigatorFor

    @GezginNavigatorFor(OrderChainRoute::class)
    class OrderChainNavigator

    @GezginNavigatorFor(ArchivedRoute::class)
    class ArchivedNavigator
""".trimIndent()

/**
 * Cross-module DISPLAY-ONLY leaf — the exact case FS5 exists to legitimize, and the gap the nav-wiring
 * PROBE closes. `SettingsRoute` is graph-less (absent from any `GraphModel` → the `routesByFq[routeFq] == null`
 * cross-module branch) AND has NO compiled `SettingsNavigator` class anywhere → the probe returns false → nav
 * wiring MUST be suppressed (no `val nav`, no `settingsNavigator`, 2-arg `bindGezgin(fragment, route)`). Under
 * the OLD `?: true` optimism this leaf was wrongly nav-wired → a `raw.settingsNavigator()` call to a
 * nonexistent factory (unresolved reference) — the relocated FS5 bug this test pins as fixed.
 */
val FRAGMENT_DISPLAY_ONLY_SOURCE = """
    package dev.gezgin.fragdisplay

    import androidx.fragment.app.Fragment
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.FragmentScreen

    data class SettingsRoute(val id: String = "x") : Route

    @FragmentScreen(SettingsRoute::class)
    class SettingsFragment : Fragment()
""".trimIndent()

/**
 * Fix-round fixture pinning the CONDITIONAL nav-wiring guard (the FS5 / SC2-MV7-parity fix). Compiled
 * ALONGSIDE [SHOP_SOURCE] so its two `@FragmentScreen` leaves target routes that ARE in this module's model
 * with a KNOWN navigator status (unlike [FRAGMENT_ROUTES], whose graph-less routes fall to the `?: true`
 * cross-module-optimistic fallback):
 * - `FeedFragment` → `HomeGraph.Feed`, which earns a `FeedNavigator` (has `@GoTo`/`@GoForResult` edges) →
 *   nav wiring MUST be emitted exactly as before (regression pin for the WITH-navigator case).
 * - `LockedFragment` → a deliberately-bare `@NoBack` route (no edges/back-edges/result-contract) →
 *   earns NO navigator → nav wiring MUST be SUPPRESSED (no `val nav`, no `lockedNavigator`
 *   import, 2-arg `bindGezgin(fragment, route)`), the edge-less-leaf display-only case.
 */
val FRAGMENT_NAV_SPLIT_SOURCE = """
    package dev.gezgin.shopfrag

    import androidx.fragment.app.Fragment
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.FragmentScreen
    import dev.gezgin.core.annotation.NoBack
    import dev.gezgin.shop.HomeGraph

    @NoBack
    data object LockedRoute : Route

    @FragmentScreen(HomeGraph.Feed::class)
    class FeedFragment : Fragment()

    @FragmentScreen(LockedRoute::class)
    class LockedFragment : Fragment()
""".trimIndent()
