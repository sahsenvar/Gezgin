@file:JvmName("FragmentRouteBundle")

package dev.gezgin.core.fragment

import android.os.Bundle
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import kotlin.jvm.Volatile
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

/**
 * The route↔Bundle serialization half of `@FragmentScreen` interop.
 *
 * A [Route] is encoded into the `arguments` Bundle **polymorphically**
 * (`PolymorphicSerializer(Route::class)`) — this is the SAME MECHANISM that `GezginKey.route` and
 * backstack PD (`RawNavigator.save`/restore) ALREADY use for a route; the polymorphism comes from
 * the app's `SerializersModule` (not from Gezgin itself). So EVERY route that survives Gezgin PD
 * survives this too by construction (b). NO new Json/module: the app-Json CARRIED by [nav]
 * (`internal val json`, now module-visible) is reused.
 */

/**
 * The constant key under which a `@FragmentScreen` route is carried as a single `String` extra in
 * the `arguments` Bundle.
 */
private const val GEZGIN_FRAGMENT_ROUTE_KEY = "dev.gezgin.fragment.route"

/**
 * The app's polymorphic [Json] (the SAME instance that feeds backstack PD), captured for
 * `gezginArgs`'s decode. **Why a process-wide holder:** `gezginArgs<Route>()` is called inside the
 * USER's bare `Fragment` subclass, with NO `RawNavigator`/composition/DI in scope — so it cannot
 * read `nav.json` directly the way `route.toBundle(nav)` (GENERATED code, `nav` in scope) does.
 * Because [toBundle] is evaluated (as the `arguments =` value) STRICTLY BEFORE `AndroidFragment`
 * instantiates the Fragment, the holder is populated before Fragment args are read on every
 * composition (fresh-process included). One `gezginSerializersModule` per app → one effective
 * `Json`, which is sufficient for route polymorphism even with multiple NavDisplays. `@Volatile`: a
 * thread-visibility safeguard (in practice always the main thread).
 */
@Volatile internal var gezginFragmentJson: Json? = null

/**
 * Process-wide setup entry point for `@FragmentScreen` interoperability.
 *
 * @author @sahsenvar
 */
public object Gezgin {
  /**
   * Prepares `@FragmentScreen` interoperability for real process death by registering the
   * application's polymorphic [json] before any Activity or FragmentManager restoration. An
   * application using `@FragmentScreen` must call this once from `Application.onCreate()`:
   * ```
   * class MyApp : Application() {
   *     override fun onCreate() {
   *         super.onCreate()
   *         Gezgin.initFragmentInterop(gezginJson)   // generated `gezginJson`
   *     }
   * }
   * ```
   *
   * A fresh process can restore a Fragment and invoke `onViewCreated` before Compose calls
   * [Route.toBundle]. Without this early initialization, `gezginArgs` has no Json instance with
   * which to decode the route. The call is unnecessary for applications that use only `@Screen`. A
   * later call replaces the process-global [Json] instance; repeated initialization is safe only
   * when every call supplies the same compatible Json and serializers-module configuration.
   * Reconfiguring it while restored Fragments may decode routes is unsupported.
   */
  public fun initFragmentInterop(json: Json) {
    gezginFragmentJson = json
  }
}

/**
 * Encodes [this] route polymorphically into a single `String` extra with [nav]'s app-Json and
 * returns a Bundle. Called from the GENERATED `provideXEntry` (`arguments = route.toBundle(raw)`) —
 * NOT a direct user-facing API. **`public` (CONTRARY to dispatch's `internal` recommendation — a
 * required deviation, /deviations):** the generated code lives in the CONSUMER module, where
 * cross-module `internal` is NOT VISIBLE; same as other codegen-called symbols like
 * `register`/navigator-factory (public but codegen-oriented). Side effect: it captures
 * [gezginFragmentJson] for `gezginArgs`'s scope-less decode path.
 */
@GezginInternalApi
public fun Route.toBundle(nav: RawNavigator): Bundle {
  gezginFragmentJson = nav.json
  val encoded = nav.json.encodeToString(PolymorphicSerializer(Route::class), this)
  return Bundle().apply { putString(GEZGIN_FRAGMENT_ROUTE_KEY, encoded) }
}

/**
 * The inverse of [toBundle] — decodes the polymorphic route in the Bundle with [json].
 * `gezginArgs`'s scope-less decode path (`gezginBoundRoute` → the captured [gezginFragmentJson])
 * calls THIS directly; there is NO separate `Bundle.toRoute(nav)` symmetric-inverse wrapper — it
 * would only forward `nav.json` to this function, was called from nowhere, and (being `Bundle`,
 * Android) could not be tested without Robolectric (the "no Robolectric" decision). The
 * serialization mechanism is still proven in `FragmentRouteSerializationTest` (commonTest, without
 * `Bundle`) via a polymorphic round-trip.
 */
internal fun decodeGezginRoute(json: Json, bundle: Bundle): Route {
  val encoded =
    requireNotNull(bundle.getString(GEZGIN_FRAGMENT_ROUTE_KEY)) {
      "Bundle does not contain '$GEZGIN_FRAGMENT_ROUTE_KEY'; this Bundle was not created by " +
        "Gezgin route.toBundle() (gezginArgs is only valid inside a @FragmentScreen Fragment hosted by Gezgin, §11.1)"
    }
  return json.decodeFromString(PolymorphicSerializer(Route::class), encoded)
}
