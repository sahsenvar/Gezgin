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
