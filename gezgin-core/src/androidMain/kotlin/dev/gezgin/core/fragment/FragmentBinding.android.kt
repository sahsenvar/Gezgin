@file:JvmName("FragmentBinding")

package dev.gezgin.core.fragment

import androidx.fragment.app.Fragment
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.Route
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty

/**
 * Task 6.2 — the LIVE-reference (`gezginNav`) and typed-arg (`gezginArgs`) access half of `@FragmentScreen`
 * interop (spec §11.1, Task 6.0 §1 "I1" split). The two delegates read from TWO DIFFERENT sources:
 * - **`gezginArgs<Route>()` → the Fragment's own `arguments` Bundle** ([decodeGezginRoute]). `arguments` is
 *   set up when the Fragment is instantiated (`setArguments`, BEFORE `onUpdate`) → it is decoded INDEPENDENTLY
 *   of `onUpdate` timing (spec 291 "route from the Bundle → PD-safe"; §B4). Since the route's PD source is
 *   Gezgin's OWN backstack (§1d), on a fresh-process restore the entry re-renders → `arguments` is
 *   regenerated with the fresh route → decode works.
 *
 *   **Validity window (STRICT contract):** `gezginArgs` is read safely in ALL cases from `onCreateView`/
 *   `onViewCreated` onward. On the FIRST-CREATION instance (`AndroidFragment` instantiates the Fragment
 *   ITSELF: the first composition evaluates `route.toBundle(raw)` BEFORE the Fragment is created →
 *   [gezginFragmentJson] is populated by then) it is safe from `onAttach`/`onCreate` onward too. BUT NOT in
 *   the fresh-process FragmentManager-RESTORE branch: after a real process-death,
 *   `FragmentActivity.onCreate(savedInstanceState)` restores the FM-saved Fragment and dispatches
 *   `onAttach`/`onCreate` to it BEFORE `setContent`'s first composition (hence `route.toBundle` in the NEW
 *   process) runs → at that moment [gezginFragmentJson] is still `null` ([gezginBoundRoute] throws a
 *   descriptive error). So reading in `onAttach`/`onCreate` is NOT guaranteed SPECIFICALLY in this restore
 *   branch; `onCreateView`/`onViewCreated` (container creation is behind the first composition → `toBundle`
 *   has always run first) is safe in both branches. The sample `HelpFragment` already reads in `onViewCreated`.
 * - **`gezginNav<Navigator>()` → the [boundRegistry] below** (filled by [bindGezgin] in `onUpdate`). A live
 *   navigator facade has NO serializable form → it must be carried in an instance-keyed registry, not in the
 *   Bundle. That is why `gezginNav` is genuinely invalid until `onUpdate` runs (spec 298).
 */

/** Registry entry: the LIVE navigator facade a `@FragmentScreen` Fragment accesses via `gezginNav`. (The
 *  route is NOT kept here — `gezginArgs` reads it from the `arguments` Bundle, §B4.) Since the navigator type
 *  is the generated `XNavigator`, gezgin-core does not know it → `Any?`; `gezginNav` casts to the reified
 *  type. If [nav] is `null`, the route gains NO navigator (an edge-less leaf — no edge/back-edge/result-contract
 *  → no generated `XNavigator`, §11.1): the Fragment IS bound but `gezginNav` cannot be read ([gezginBoundNav]
 *  throws FS5) — this is DISTINCT from "not bound yet" (NO record at all in the registry). */
private class BoundGezgin(val nav: Any?)

/**
 * Fragment-instance → live navigator side-table. **Weak key (`WeakHashMap`):** a Fragment recreated on
 * config-change/PD is a DIFFERENT instance (a new weak key) → the old entry is GC'd, so the registry
 * naturally tracks the live instance (Task 6.0 §1c). Set up/read on the main thread; no synchronization.
 */
private val boundRegistry = WeakHashMap<Fragment, BoundGezgin>()

/**
 * Called from GENERATED code in `AndroidFragment.onUpdate` with the LIVE Fragment instance (Task 6.0 §1b) +
 * the route + its navigator (`onUpdate = { fragment -> bindGezgin(fragment, route, nav) }`). Registers the
 * live navigator under the instance.
 *
 * **UNCONDITIONAL put — NO bind-once/skip-if-present guard (Task 6.0 review, critical):** `onUpdate` runs once
 * per live instance (§1c); for a NEW instance after config-change/PD it must run again to re-bind — an
 * "already bound, skip" defense is the one thing that BREAKS this path. Overwriting with equal data is
 * harmless (idempotent-by-construction). `public`: the generated code lives in the CONSUMER module
 * (cross-module `internal` is not visible).
 *
 * [route] is not currently used by the registry (args are carried in the Bundle, §B4) but is KEPT in the
 * signature so it matches the generated `onUpdate { bindGezgin(fragment, route, nav) }` shape exactly and
 * leaves the door open to future registry-based arg access.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route, nav: Any) {
    boundRegistry[fragment] = BoundGezgin(nav)
}

/**
 * The NAV-LESS overload of [bindGezgin] — for when a route gains NO navigator (an edge-less leaf: no
 * @GoTo/@ReplaceTo/@BackTo/... edge, no result-contract → [dev.gezgin.processor.codegen.NavigatorCodegen]
 * does NOT generate an `XNavigator` for it, §11.1). In this case the GENERATED `provideXEntry` never binds
 * `nav` and calls `onUpdate = { fragment -> bindGezgin(fragment, route) }` (it NEVER calls a non-existent
 * factory — the phase-later counterpart of SC2/MV7; the condition is computed in
 * [dev.gezgin.processor.GezginProcessor] via `NavigatorCodegen.hasNavigator`). It BINDS the Fragment without a
 * navigator: `gezginArgs` (from the Bundle) still works; but if `gezginNav` is read, [gezginBoundNav] throws
 * an `[FS5]` error — not "not bound" but "NO navigator". A legitimate display-only brownfield screen
 * (Settings/About) follows this path; it is NOT REJECTED at KSP time.
 *
 * **UNCONDITIONAL put** — the same idempotency contract as the nav-ful overload (NO bind-once guard): a new
 * instance after config-change/PD must re-bind. `public`: the generated code lives in the consumer module.
 */
@GezginInternalApi
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route) {
    boundRegistry[fragment] = BoundGezgin(nav = null)
}

/**
 * `gezginNav`'s registry read + TWO separate descriptive errors (the non-inline real body; [gezginNav] only
 * inlines the reified cast → `boundRegistry`/`BoundGezgin` can stay private):
 * - if there is NO record in the registry → "not bound yet" (`onUpdate` has not run).
 * - if there is a record but `nav == null` → `[FS5]` "the route has NO navigator" (an edge-less leaf, bound
 *   via the nav-less [bindGezgin]). These two cases are DELIBERATELY separated with distinct messages.
 */
@PublishedApi
internal fun gezginBoundNav(fragment: Fragment): Any {
    val bound = boundRegistry[fragment] ?: error(
        "gezginNav yalnız AndroidFragment'ın onUpdate'i bu Fragment instance'ını bind ettikten SONRA " +
            "okunabilir (bind = ilk onUpdate; §11.1 lifecycle sözleşmesi). ${fragment::class.simpleName} " +
            "için henüz bind yok — canlı navigator serileştirilemez, arguments'tan gelemez.",
    )
    return bound.nav ?: error(
        "[FS5] gezginNav okunamaz: ${fragment::class.simpleName}'ın @FragmentScreen route'unun bir " +
            "navigator'ı YOK — hiç @GoTo/@ReplaceTo/@BackTo/... edge'i (ve result-contract'ı) tanımlamıyor, " +
            "dolayısıyla NavigatorCodegen ona bir XNavigator ÜRETMEDİ (bu Fragment navigator'sız bağlandı). " +
            "Bu 'henüz bind edilmedi'den FARKLI bir durumdur. Çözüm: gezginNav'ı yalnız en az bir navigasyon " +
            "edge'i tanımlayan bir route'ta kullan (route'a bir edge ekle) ya da bu görüntü-yalnızca ekrandan " +
            "gezginNav delegesini kaldır (yalnız gezginArgs kullan).",
    )
}

/**
 * The Fragment counterpart of `@Screen`'s `nav` param (§11.1). `by gezginNav<XNavigator>()` — returns the
 * post-bind live navigator cast to the reified type. [gezginBoundNav] separates two cases with distinct
 * errors: if not bound yet, "not bound"; if the route has no navigator (an edge-less leaf, bound via the
 * nav-less [bindGezgin]), `[FS5]` "NO navigator". (Usually the second is already caught at compile time: a
 * route without a navigator has NO `XNavigator` type generated → `gezginNav<XNavigator>()` cannot resolve;
 * FS5 is a residual runtime net.)
 */
public inline fun <reified N> gezginNav(): ReadOnlyProperty<Fragment, N> =
    ReadOnlyProperty { fragment, _ -> gezginBoundNav(fragment) as N }

/**
 * `gezginArgs`'s `arguments`-Bundle read (the non-inline real body; [gezginArgs] only inlines the reified cast
 * → [gezginFragmentJson]/[decodeGezginRoute] can stay `internal`). It decodes from the Bundle, INDEPENDENTLY
 * of `onUpdate` (§B4) — if [gezginFragmentJson] is null (in practice never: `toBundle` populates it before the
 * Fragment is created), a descriptive error.
 */
@PublishedApi
internal fun gezginBoundRoute(fragment: Fragment): Route {
    val json = gezginFragmentJson ?: error(
        "gezginArgs okunmadan önce hiç Gezgin route.toBundle() değerlendirilmemiş — @FragmentScreen'li " +
            "Fragment yalnız Gezgin'in (GezginDisplay + üretilen provideXEntry) host ettiği bir entry olarak " +
            "geçerlidir (§11.1). Bu, process-death sonrası FragmentManager'ın Fragment'ı onCreate'te henüz " +
            "route.toBundle() çalışmadan geri yüklediği durumda da olabilir — gezginArgs'ı onAttach/onCreate'te " +
            "DEĞİL, onCreateView/onViewCreated'da okuyun (§B4).",
    )
    val bundle = fragment.arguments ?: error(
        "gezginArgs: ${fragment::class.simpleName}.arguments null — Fragment Gezgin route.toBundle() ile " +
            "kurulan arguments taşımıyor (yalnız @FragmentScreen host'unda geçerli, §11.1).",
    )
    return decodeGezginRoute(json, bundle)
}

/**
 * The Fragment counterpart of `@Screen`'s `route` param (§11.1). `by gezginArgs<XRoute>()` — decodes the typed
 * route from the Fragment's `arguments` Bundle and casts to the reified type. INDEPENDENT of `onUpdate`
 * (arguments are set up at instantiation time). **Validity:** PD-safe in ALL cases from `onCreateView`/
 * `onViewCreated` onward; in `onAttach`/`onCreate` it is safe only on the FIRST-CREATION instance, NOT in the
 * fresh-process FragmentManager-restore branch (at that moment [gezginFragmentJson] is still `null` — see the
 * file-level KDoc + the [gezginBoundRoute] error). Read the route in `onCreateView`/`onViewCreated` (§B4).
 */
public inline fun <reified R : Route> gezginArgs(): ReadOnlyProperty<Fragment, R> =
    ReadOnlyProperty { fragment, _ -> gezginBoundRoute(fragment) as R }
