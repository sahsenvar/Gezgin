@file:JvmName("FragmentBinding")

package dev.gezgin.core.fragment

import androidx.fragment.app.Fragment
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.Route
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The live-reference (`gezginNav`) and typed-argument (`gezginArgs`) access half of
 * `@FragmentScreen` interop. The two delegates read from two different sources:
 * - **`gezginArgs<Route>()` → the Fragment's own `arguments` Bundle** ([decodeGezginRoute]).
 *   `arguments` is set up when the Fragment is instantiated (`setArguments`, BEFORE `onUpdate`) →
 *   it is decoded independently of `onUpdate` timing. Since the route's process-death source is
 *   Gezgin's own back stack, on a fresh-process restore the entry re-renders → `arguments` is
 *   regenerated with the fresh route → decode works.
 *
 *   **Validity window (STRICT contract):** on the FIRST-CREATION instance (`AndroidFragment`
 *   instantiates the Fragment ITSELF: the first composition evaluates `route.toBundle(raw)` BEFORE
 *   the Fragment is created → [gezginFragmentJson] is populated by then) `gezginArgs` is safe from
 *   `onAttach`/`onCreate` onward.
 *
 *   **Process-death caveat:** after a true process death,
 *   `FragmentActivity.onCreate(savedInstanceState)` restores the FM-saved Fragment and dispatches
 *   `onAttach`, `onCreate`, `onCreateView` and even `onViewCreated` to it BEFORE `setContent`'s
 *   first composition (hence before `route.toBundle` in the NEW process) runs. So in the
 *   fresh-process restore branch [gezginFragmentJson] is still `null` EVEN in `onViewCreated`
 *   before the first composition during FragmentManager restore, so `gezginArgs` would throw. A
 *   later lifecycle callback cannot solve this because there is no Fragment callback guaranteed to
 *   run after the first composition; it is registering the app-Json at PROCESS STARTUP via
 *   [Gezgin.initFragmentInterop] in `Application.onCreate` (runs before FM restore). With that init
 *   done, `gezginArgs` is safe from `onCreateView`/`onViewCreated` onward in BOTH branches. The
 *   sample `HelpFragment` reads in `onViewCreated`; the sample `Application` calls
 *   `Gezgin.initFragmentInterop(gezginJson)`. (Config-change/DKA keeps the process alive → static
 *   stays populated → this only matters for TRUE process death.)
 * - **`gezginNav<Navigator>()` → the [boundRegistry] below** (filled by [bindGezgin] in
 *   `onUpdate`). A live navigator facade has NO serializable form → it must be carried in an
 *   instance-keyed registry, not in the Bundle. That is why `gezginNav` is genuinely invalid until
 *   `onUpdate` runs.
 */

/**
 * Registry entry: the LIVE navigator facade a `@FragmentScreen` Fragment accesses via `gezginNav`.
 * (The route is NOT kept here — `gezginArgs` reads it from the `arguments` Bundle.) Since the
 * navigator type is the generated `XNavigator`, gezgin-core does not know it → `Any?`; `gezginNav`
 * casts to the reified type. If [nav] is `null`, the route gains NO navigator (an edge-less leaf —
 * no edge/back-edge/result-contract → no generated `XNavigator`): the Fragment IS bound but
 * `gezginNav` cannot be read ([gezginBoundNav] throws `FS5`) — this is DISTINCT from "not bound
 * yet" (NO record at all in the registry).
 */
private class BoundGezgin(val nav: Any?)

/**
 * Post-bind hook for a `@FragmentScreen` Fragment whose route declares a `@GoForResult` edge. The
 * Fragment must collect the generated `xResults` flow after its navigator is available.
 *
 * `gezginNav` is bound inside `AndroidFragment.onUpdate`, which runs after the internal
 * `commitNow()`. Therefore, `gezginNav` is not yet bound in `onCreateView`/`onViewCreated` — a
 * fragment cannot start a `nav.xResults` collector there, and there was no callback that says
 * "binding is complete". Without this hook a `@GoForResult` launcher fragment compiles but is
 * practically broken (the delivered result is never observed until some later interaction re-reads
 * it).
 *
 * **Contract:** [bindGezgin] calls [onGezginBound] on the MAIN thread right after it registers the
 * navigator — which is after `commitNow`, so `onCreateView`/`onViewCreated` have already run and
 * `viewLifecycleOwner` exists. `gezginNav` is GUARANTEED readable here (it was just bound). Start
 * the result collector in the fragment's `viewLifecycleOwner` scope, e.g.:
 * ```
 * class PickerFragment : Fragment(R.layout.picker), GezginBindingObserver {
 *     private val nav by gezginNav<PickerNavigator>()
 *     override fun onGezginBound() {
 *         viewLifecycleOwner.lifecycleScope.launch {
 *             viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
 *                 nav.chooseAddressResults.collect { result -> /* handle */ }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * After configuration change or process recreation, the fragment is a new instance and `bindGezgin`
 * (hence [onGezginBound]) runs again. The collector is reattached to the fresh
 * `viewLifecycleOwner`. It fires once per live instance because binding runs once per instance, so
 * it does not duplicate collectors. If the route is an edge-less leaf (NO navigator),
 * [onGezginBound] is still invoked, but `gezginNav` throws `FS5`; a display-only leaf should not
 * implement this interface.
 *
 * @author @sahsenvar
 */
public interface GezginBindingObserver {
  /**
   * Called on the main thread right after this Fragment's navigator is bound (post-`commitNow`).
   */
  public fun onGezginBound()
}

/**
 * Fragment-instance → live navigator side-table. **Weak key (`WeakHashMap`):** a Fragment recreated
 * on config-change/PD is a DIFFERENT instance (a new weak key) → the old entry is GC'd, so the
 * registry naturally tracks the live instance. Set up/read on the main thread; no synchronization.
 */
private val boundRegistry = WeakHashMap<Fragment, BoundGezgin>()

/**
 * Called from generated `AndroidFragment.onUpdate` code with the live Fragment instance, route, and
 * navigator (`onUpdate = { fragment -> bindGezgin(fragment, route, nav) }`). Registers the live
 * navigator under the instance.
 *
 * **Unconditional put — no bind-once/skip-if-present guard:** `onUpdate` runs once per live
 * instance; for a NEW instance after config-change/PD it must run again to re-bind — an "already
 * bound, skip" defense is the one thing that BREAKS this path. Overwriting with equal data is
 * harmless (idempotent-by-construction). `public`: the generated code lives in the CONSUMER module
 * (cross-module `internal` is not visible).
 *
 * [route] is not currently used by the registry (args are carried in the Bundle) but is KEPT in the
 * signature so it matches the generated `onUpdate { bindGezgin(fragment, route, nav) }` shape
 * exactly and leaves the door open to future registry-based arg access.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route, nav: Any) {
  boundRegistry[fragment] = BoundGezgin(nav)
  // Invoke after registration so onGezginBound can read gezginNav. CommitNow has completed, which
  // means viewLifecycleOwner is ready and a result-launching Fragment can safely collect xResults.
  (fragment as? GezginBindingObserver)?.onGezginBound()
}

/**
 * The navigator-free overload of [bindGezgin] handles an edge-less route with no navigation edge or
 * result contract. Such a route has no generated `XNavigator`, so the generated entry binds the
 * Fragment without a navigator. `gezginArgs` still works because it reads from the Bundle, while
 * `gezginNav` fails with `FS5`. This permits display-only brownfield screens such as Settings or
 * About.
 *
 * The registry write is unconditional so a new instance after configuration change or process
 * recreation can bind again. This function is public because generated code runs in the consumer
 * module.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route) {
  boundRegistry[fragment] = BoundGezgin(nav = null)
  // A navigator-free leaf is still bound and receives the completion callback. Reading gezginNav
  // remains invalid for such a leaf and produces the documented `FS5` error.
  (fragment as? GezginBindingObserver)?.onGezginBound()
}

/**
 * Reads the registry for [gezginNav] while keeping the registry implementation private. A missing
 * record means `onUpdate` has not bound the Fragment yet. A record with a null navigator means the
 * bound route is an edge-less leaf; this fails with `FS5`.
 */
@PublishedApi
internal fun gezginBoundNav(fragment: Fragment): Any {
  val bound =
    boundRegistry[fragment]
      ?: error(
        "gezginNav can only be read after AndroidFragment.onUpdate binds this Fragment instance " +
          "(bind = first onUpdate; §11.1 lifecycle contract). ${fragment::class.simpleName} " +
          "has not been bound yet; a live navigator cannot be serialized or read from arguments."
      )
  return bound.nav
    ?: error(
      "[FS5] gezginNav cannot be read: ${fragment::class.simpleName}'s @FragmentScreen route has no " +
        "navigator because it declares no @GoTo/@ReplaceTo/@BackTo/... edge and no result contract, " +
        "so NavigatorCodegen did not generate an XNavigator for it (this Fragment was bound without a navigator). " +
        "This is different from 'not bound yet'. Use gezginNav only on a route that defines at least one navigation " +
        "edge, or remove the gezginNav delegate from this display-only screen and use only gezginArgs."
    )
}

/**
 * Returns the live generated navigator after the Fragment has been bound. [gezginBoundNav]
 * distinguishes a Fragment that is not yet bound from an edge-less route that has no navigator. The
 * latter normally fails at compile time because no `XNavigator` type is generated; `FS5` remains as
 * a runtime safeguard.
 */
public inline fun <reified N> gezginNav(): ReadOnlyProperty<Fragment, N> =
  ReadOnlyProperty { fragment, _ ->
    gezginBoundNav(fragment) as N
  }

/**
 * `gezginArgs`'s `arguments`-Bundle read (the non-inline real body; [gezginArgs] only inlines the
 * reified cast → [gezginFragmentJson]/[decodeGezginRoute] can stay `internal`). It decodes from the
 * Bundle, INDEPENDENTLY of `onUpdate` — if [gezginFragmentJson] is null (in practice never:
 * `toBundle` populates it before the Fragment is created), a descriptive error.
 */
@PublishedApi
internal fun gezginBoundRoute(fragment: Fragment): Route {
  val json =
    gezginFragmentJson
      ?: error(
        "gezginArgs: Gezgin's polymorphic Json is not registered yet. If you use @FragmentScreen, call " +
          "Gezgin.initFragmentInterop(gezginJson) ONCE in your Application.onCreate() — after a REAL " +
          "process death, FragmentManager restores the Fragment (up to onViewCreated) during " +
          "Activity.onCreate BEFORE setContent composes (before route.toBundle() runs), so the app-Json " +
          "must already be registered at process startup. (Config-change/DKA keeps the process alive, so " +
          "this only bites true process death.) A @FragmentScreen Fragment is otherwise only valid as an " +
          "entry hosted by Gezgin (GezginDisplay + generated provideXEntry, §11.1)."
      )
  val bundle =
    fragment.arguments
      ?: error(
        "gezginArgs: ${fragment::class.simpleName}.arguments is null; the Fragment does not have arguments " +
          "created by Gezgin route.toBundle() (only valid in a @FragmentScreen host, §11.1)."
      )
  return decodeGezginRoute(json, bundle)
}

/**
 * Memoizing `ReadOnlyProperty` behind [gezginArgs]. Decodes the route from the `arguments` Bundle
 * ONCE (on the first successful read) and caches it; every later read of the same delegate returns
 * the SAME instance (no per-read JSON re-decode, no fresh-instance-per-read foot-gun). The delegate
 * is created per `by gezginArgs()` property, which is per fragment-instance, so the cache is
 * correctly instance-scoped. A FAILED first read (fresh-process restore before `toBundle`,
 * [gezginBoundRoute] throws) does NOT cache → the strict validity window is preserved and a later
 * `onViewCreated` read still succeeds.
 */
@PublishedApi
internal class GezginArgsProperty<R : Route>(private val cast: (Route) -> R) :
  ReadOnlyProperty<Fragment, R> {
  private var cached: R? = null

  override fun getValue(thisRef: Fragment, property: KProperty<*>): R =
    cached ?: cast(gezginBoundRoute(thisRef)).also { cached = it }
}

/**
 * The Fragment counterpart of `@Screen`'s `route` param. `by gezginArgs<XRoute>()` — decodes the
 * typed route from the Fragment's `arguments` Bundle and casts to the reified type. INDEPENDENT of
 * `onUpdate` (arguments are set up at instantiation time). **Memoized:** decodes ONCE per fragment
 * instance and returns a STABLE instance on repeated reads (see [GezginArgsProperty]).
 * **Validity:** PD-safe in ALL cases from `onCreateView`/`onViewCreated` onward; in
 * `onAttach`/`onCreate` it is safe only on the FIRST-CREATION instance, NOT in the fresh-process
 * FragmentManager-restore branch (at that moment [gezginFragmentJson] is still `null` — see the
 * file-level KDoc + the [gezginBoundRoute] error). Read the route in `onCreateView`/
 * `onViewCreated`.
 */
public inline fun <reified R : Route> gezginArgs(): ReadOnlyProperty<Fragment, R> =
  GezginArgsProperty {
    it as R
  }
