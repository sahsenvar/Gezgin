@file:JvmName("FragmentBinding")

package dev.gezgin.core.fragment

import androidx.fragment.app.Fragment
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.Route
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Task 6.2 — the LIVE-reference (`gezginNav`) and typed-arg (`gezginArgs`) access half of
 * `@FragmentScreen` interop (spec §11.1, Task 6.0 §1 "I1" split). The two delegates read from TWO
 * DIFFERENT sources:
 * - **`gezginArgs<Route>()` → the Fragment's own `arguments` Bundle** ([decodeGezginRoute]).
 *   `arguments` is set up when the Fragment is instantiated (`setArguments`, BEFORE `onUpdate`) →
 *   it is decoded INDEPENDENTLY of `onUpdate` timing (spec 291 "route from the Bundle → PD-safe";
 *   §B4). Since the route's PD source is Gezgin's OWN backstack (§1d), on a fresh-process restore
 *   the entry re-renders → `arguments` is regenerated with the fresh route → decode works.
 *
 *   **Validity window (STRICT contract):** on the FIRST-CREATION instance (`AndroidFragment`
 *   instantiates the Fragment ITSELF: the first composition evaluates `route.toBundle(raw)` BEFORE
 *   the Fragment is created → [gezginFragmentJson] is populated by then) `gezginArgs` is safe from
 *   `onAttach`/`onCreate` onward.
 *
 *   **REAL process-death caveat (device-verified 2026-07-12, was previously WRONG here):** after a
 *   true process death, `FragmentActivity.onCreate(savedInstanceState)` restores the FM-saved
 *   Fragment and dispatches `onAttach`, `onCreate`, `onCreateView` and even `onViewCreated` to it
 *   BEFORE `setContent`'s first composition (hence before `route.toBundle` in the NEW process)
 *   runs. So in the fresh-process restore branch [gezginFragmentJson] is still `null` EVEN in
 *   `onViewCreated` (the old claim that `onViewCreated` is "behind the first composition" is FALSE
 *   for FM-restore) → `gezginArgs` would throw. The fix is NOT a later lifecycle callback (there is
 *   no Fragment callback guaranteed to run after the first composition); it is registering the
 *   app-Json at PROCESS STARTUP via [Gezgin.initFragmentInterop] in `Application.onCreate` (runs
 *   before FM restore). With that init done, `gezginArgs` is safe from
 *   `onCreateView`/`onViewCreated` onward in BOTH branches. The sample `HelpFragment` reads in
 *   `onViewCreated`; the sample `Application` calls `Gezgin.initFragmentInterop(gezginJson)`.
 *   (Config-change/DKA keeps the process alive → static stays populated → this only matters for
 *   TRUE process death.)
 * - **`gezginNav<Navigator>()` → the [boundRegistry] below** (filled by [bindGezgin] in
 *   `onUpdate`). A live navigator facade has NO serializable form → it must be carried in an
 *   instance-keyed registry, not in the Bundle. That is why `gezginNav` is genuinely invalid until
 *   `onUpdate` runs (spec 298).
 */

/**
 * Registry entry: the LIVE navigator facade a `@FragmentScreen` Fragment accesses via `gezginNav`.
 * (The route is NOT kept here — `gezginArgs` reads it from the `arguments` Bundle, §B4.) Since the
 * navigator type is the generated `XNavigator`, gezgin-core does not know it → `Any?`; `gezginNav`
 * casts to the reified type. If [nav] is `null`, the route gains NO navigator (an edge-less leaf —
 * no edge/back-edge/result-contract → no generated `XNavigator`, §11.1): the Fragment IS bound but
 * `gezginNav` cannot be read ([gezginBoundNav] throws FS5) — this is DISTINCT from "not bound yet"
 * (NO record at all in the registry).
 */
private class BoundGezgin(val nav: Any?)

/**
 * E-MJ-F1 — the reliable post-bind hook a `@FragmentScreen` Fragment implements when it is a result
 * LAUNCHER (its route declares a `@GoForResult` edge, so it must collect the generated `xResults`
 * Flow).
 *
 * **Why this is needed:** `gezginNav` is bound inside `AndroidFragment.onUpdate`, which runs AFTER
 * the internal `commitNow()` (fragment-compose 1.8.9 fact). So `gezginNav` is NOT yet bound in
 * `onCreateView`/ `onViewCreated` — a fragment cannot start a `nav.xResults` collector there, and
 * there was no callback that says "binding is complete". Without this hook a `@GoForResult`
 * launcher fragment compiles but is practically broken (the delivered result is never observed
 * until some later interaction re-reads it).
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
 * On config-change/PD the fragment is a NEW instance and `bindGezgin` (hence [onGezginBound]) runs
 * again for it → the collector is re-attached against the fresh `viewLifecycleOwner`. It fires ONCE
 * per live instance (bind is once-per-instance, §1c) → no duplicate collectors on the same
 * instance. If the route is an edge-less leaf (NO navigator), [onGezginBound] is still invoked, but
 * `gezginNav` throws `[FS5]` — a display-only leaf should NOT implement this interface.
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
 * registry naturally tracks the live instance (Task 6.0 §1c). Set up/read on the main thread; no
 * synchronization.
 */
private val boundRegistry = WeakHashMap<Fragment, BoundGezgin>()

/**
 * Called from GENERATED code in `AndroidFragment.onUpdate` with the LIVE Fragment instance (Task
 * 6.0 §1b) + the route + its navigator (`onUpdate = { fragment -> bindGezgin(fragment, route, nav)
 * }`). Registers the live navigator under the instance.
 *
 * **UNCONDITIONAL put — NO bind-once/skip-if-present guard (Task 6.0 review, critical):**
 * `onUpdate` runs once per live instance (§1c); for a NEW instance after config-change/PD it must
 * run again to re-bind — an "already bound, skip" defense is the one thing that BREAKS this path.
 * Overwriting with equal data is harmless (idempotent-by-construction). `public`: the generated
 * code lives in the CONSUMER module (cross-module `internal` is not visible).
 *
 * [route] is not currently used by the registry (args are carried in the Bundle, §B4) but is KEPT
 * in the signature so it matches the generated `onUpdate { bindGezgin(fragment, route, nav) }`
 * shape exactly and leaves the door open to future registry-based arg access.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route, nav: Any) {
  boundRegistry[fragment] = BoundGezgin(nav)
  // E-MJ-F1 — post-bind hook: registry SET edildikten SONRA çağrılır (onGezginBound içinde
  // gezginNav
  // okunabilir). commitNow SONRASI çalıştığından viewLifecycleOwner hazır → result-LAUNCHER
  // fragment'ı
  // xResults collector'ını burada güvenle kurar. İmplement etmeyen fragment'lar için no-op.
  (fragment as? GezginBindingObserver)?.onGezginBound()
}

/**
 * The NAV-LESS overload of [bindGezgin] — for when a route gains NO navigator (an edge-less leaf:
 * no
 *
 * @GoTo/@ReplaceTo/@BackTo/... edge, no result-contract → `NavigatorCodegen` does NOT generate an
 *   `XNavigator` for it, §11.1). In this case the GENERATED `provideXEntry` never binds `nav` and
 *   calls `onUpdate = { fragment -> bindGezgin(fragment, route) }` (it NEVER calls a non-existent
 *   factory — the phase-later counterpart of SC2/MV7; the condition is computed in
 *   `GezginProcessor` via `NavigatorCodegen.hasNavigator`). It BINDS the Fragment without a
 *   navigator: `gezginArgs` (from the Bundle) still works; but if `gezginNav` is read,
 *   [gezginBoundNav] throws an `[FS5]` error — not "not bound" but "NO navigator". A legitimate
 *   display-only brownfield screen (Settings/About) follows this path; it is NOT REJECTED at KSP
 *   time.
 *
 * **UNCONDITIONAL put** — the same idempotency contract as the nav-ful overload (NO bind-once
 * guard): a new instance after config-change/PD must re-bind. `public`: the generated code lives in
 * the consumer module.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route) {
  boundRegistry[fragment] = BoundGezgin(nav = null)
  // E-MJ-F1 — nav'sız leaf de bind edilir; onGezginBound çağrılır ama gezginNav [FS5] fırlatır
  // (leaf
  // ekran bu interface'i implement ETMEMELİ). Simetri için ve "bind tamamlandı" sinyali olarak
  // çağrılır.
  (fragment as? GezginBindingObserver)?.onGezginBound()
}

/**
 * `gezginNav`'s registry read + TWO separate descriptive errors (the non-inline real body;
 * [gezginNav] only inlines the reified cast → `boundRegistry`/`BoundGezgin` can stay private):
 * - if there is NO record in the registry → "not bound yet" (`onUpdate` has not run).
 * - if there is a record but `nav == null` → `[FS5]` "the route has NO navigator" (an edge-less
 *   leaf, bound via the nav-less [bindGezgin]). These two cases are DELIBERATELY separated with
 *   distinct messages.
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
 * The Fragment counterpart of `@Screen`'s `nav` param (§11.1). `by gezginNav<XNavigator>()` —
 * returns the post-bind live navigator cast to the reified type. [gezginBoundNav] separates two
 * cases with distinct errors: if not bound yet, "not bound"; if the route has no navigator (a bare
 * `@NoBack` leaf, bound via the nav-less [bindGezgin]), `[FS5]` "NO navigator". (Usually the second
 * is already caught at compile time: a route without a navigator has NO `XNavigator` type generated
 * → `gezginNav<XNavigator>()` cannot resolve; FS5 is a residual runtime net.)
 */
public inline fun <reified N> gezginNav(): ReadOnlyProperty<Fragment, N> =
  ReadOnlyProperty { fragment, _ ->
    gezginBoundNav(fragment) as N
  }

/**
 * `gezginArgs`'s `arguments`-Bundle read (the non-inline real body; [gezginArgs] only inlines the
 * reified cast → [gezginFragmentJson]/[decodeGezginRoute] can stay `internal`). It decodes from the
 * Bundle, INDEPENDENTLY of `onUpdate` (§B4) — if [gezginFragmentJson] is null (in practice never:
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
 * mN2 — the memoizing `ReadOnlyProperty` behind [gezginArgs]. Decodes the route from the
 * `arguments` Bundle ONCE (on the first successful read) and caches it; every later read of the
 * same delegate returns the SAME instance (no per-read JSON re-decode, no fresh-instance-per-read
 * foot-gun). The delegate is created per `by gezginArgs()` property, which is per
 * fragment-instance, so the cache is correctly instance-scoped. A FAILED first read (fresh-process
 * restore before `toBundle`, [gezginBoundRoute] throws) does NOT cache → the strict validity window
 * (§B4) is preserved and a later `onViewCreated` read still succeeds.
 */
@PublishedApi
internal class GezginArgsProperty<R : Route>(private val cast: (Route) -> R) :
  ReadOnlyProperty<Fragment, R> {
  private var cached: R? = null

  override fun getValue(thisRef: Fragment, property: KProperty<*>): R =
    cached ?: cast(gezginBoundRoute(thisRef)).also { cached = it }
}

/**
 * The Fragment counterpart of `@Screen`'s `route` param (§11.1). `by gezginArgs<XRoute>()` —
 * decodes the typed route from the Fragment's `arguments` Bundle and casts to the reified type.
 * INDEPENDENT of `onUpdate` (arguments are set up at instantiation time). **Memoized (mN2):**
 * decodes ONCE per fragment instance and returns a STABLE instance on repeated reads (see
 * [GezginArgsProperty]). **Validity:** PD-safe in ALL cases from `onCreateView`/`onViewCreated`
 * onward; in `onAttach`/`onCreate` it is safe only on the FIRST-CREATION instance, NOT in the
 * fresh-process FragmentManager-restore branch (at that moment [gezginFragmentJson] is still `null`
 * — see the file-level KDoc + the [gezginBoundRoute] error). Read the route in `onCreateView`/
 * `onViewCreated` (§B4).
 */
public inline fun <reified R : Route> gezginArgs(): ReadOnlyProperty<Fragment, R> =
  GezginArgsProperty {
    it as R
  }
