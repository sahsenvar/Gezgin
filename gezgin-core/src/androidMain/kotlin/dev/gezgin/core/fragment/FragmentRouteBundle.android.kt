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
 * Task 6.2 — the route↔Bundle serialization half of `@FragmentScreen` interop (spec §11.1, Task 6.0 §3).
 *
 * A [Route] is encoded into the `arguments` Bundle **polymorphically** (`PolymorphicSerializer(Route::class)`)
 * — this is the SAME MECHANISM that `GezginKey.route` and backstack PD (`RawNavigator.save`/restore) ALREADY
 * use for a route; the polymorphism comes from the app's `SerializersModule` (not from Gezgin itself). So
 * EVERY route that survives Gezgin PD survives this too by construction (§3b). NO new Json/module: the
 * app-Json CARRIED by [nav] (`internal val json`, now module-visible) is reused.
 */

/** The constant key under which a `@FragmentScreen` route is carried as a single `String` extra in the `arguments` Bundle. */
private const val GEZGIN_FRAGMENT_ROUTE_KEY = "dev.gezgin.fragment.route"

/**
 * The app's polymorphic [Json] (the SAME instance that feeds backstack PD), captured for `gezginArgs`'s
 * decode. **Why a process-wide holder (§B4 decision):** `gezginArgs<Route>()` is called inside the USER's
 * bare `Fragment` subclass, with NO `RawNavigator`/composition/DI in scope — so it cannot read `nav.json`
 * directly the way `route.toBundle(nav)` (GENERATED code, `nav` in scope) does. Because [toBundle] is
 * evaluated (as the `arguments =` value) STRICTLY BEFORE `AndroidFragment` instantiates the Fragment, the
 * holder is populated before Fragment args are read on every composition (fresh-process included). One
 * `gezginSerializersModule` per app (§3.3) → one effective Json; equivalent for route polymorphism even with
 * multiple NavDisplays. `@Volatile`: a thread-visibility safeguard (in practice always the main thread).
 */
@Volatile
internal var gezginFragmentJson: Json? = null

/** `@FragmentScreen` interop için process-genelinde kurulum kancası. */
public object Gezgin {
    /**
     * `@FragmentScreen` interop'unu GERÇEK process-death'e karşı hazırlar: app'in polimorfik [json]'unu
     * ([gezginFragmentJson]) process açılışında — herhangi bir Activity/FragmentManager restore'undan ÖNCE —
     * kaydeder. `@FragmentScreen` KULLANAN bir uygulama bunu `Application.onCreate()`'te BİR KEZ çağırmalıdır:
     * ```
     * class MyApp : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *         Gezgin.initFragmentInterop(gezginJson)   // generated `gezginJson`
     *     }
     * }
     * ```
     * **Neden gerekli:** taze process'te FragmentManager, `@FragmentScreen` Fragment'ı `Activity.onCreate`'in
     * `super`'inde — `setContent` kompozisyonundan (dolayısıyla [Route.toBundle]'ın [gezginFragmentJson]'u
     * doldurmasından) ÖNCE — restore edip `onViewCreated`'a kadar dispatch eder; o an statik `null` olduğundan
     * `gezginArgs` decode edecek Json'u bulamaz ve fırlatır. Bu çağrı statiği erken doldurup o pencereyi kapatır.
     * Config-change/DKA'da process yaşadığı için zaten doluydu; bu YALNIZ gerçek process-death için gerekir.
     * `@FragmentScreen` kullanmayan (yalnız `@Screen`) uygulamalar için gereksizdir. Idempotent — birden çok
     * çağrı ya da sonraki `toBundle` yeniden-set'i zararsızdır (tek `gezginSerializersModule` → tek etkin Json).
     */
    public fun initFragmentInterop(json: Json) {
        gezginFragmentJson = json
    }
}

/**
 * Encodes [this] route polymorphically into a single `String` extra with [nav]'s app-Json and returns a
 * Bundle. Called from the GENERATED `provideXEntry` (`arguments = route.toBundle(raw)`) — NOT a direct
 * user-facing API. **`public` (CONTRARY to dispatch's `internal` recommendation — a required deviation,
 * §B4/deviations):** the generated code lives in the CONSUMER module, where cross-module `internal` is NOT
 * VISIBLE; same as other codegen-called symbols like `register`/navigator-factory (public but
 * codegen-oriented). Side effect: it captures [gezginFragmentJson] for `gezginArgs`'s scope-less decode path.
 */
@GezginInternalApi
public fun Route.toBundle(nav: RawNavigator): Bundle {
    gezginFragmentJson = nav.json
    val encoded = nav.json.encodeToString(PolymorphicSerializer(Route::class), this)
    return Bundle().apply { putString(GEZGIN_FRAGMENT_ROUTE_KEY, encoded) }
}

/**
 * The inverse of [toBundle] — decodes the polymorphic route in the Bundle with [json]. `gezginArgs`'s
 * scope-less decode path (`gezginBoundRoute` → the captured [gezginFragmentJson]) calls THIS directly; there
 * is NO separate `Bundle.toRoute(nav)` symmetric-inverse wrapper — it would only forward `nav.json` to this
 * function, was called from nowhere, and (being `Bundle`, Android) could not be tested without Robolectric
 * (Task 6.0's "no Robolectric" decision). The serialization mechanism is still proven in
 * `FragmentRouteSerializationTest` (commonTest, without `Bundle`) via a polymorphic round-trip.
 */
internal fun decodeGezginRoute(json: Json, bundle: Bundle): Route {
    val encoded = requireNotNull(bundle.getString(GEZGIN_FRAGMENT_ROUTE_KEY)) {
        "Bundle does not contain '$GEZGIN_FRAGMENT_ROUTE_KEY'; this Bundle was not created by " +
            "Gezgin route.toBundle() (gezginArgs is only valid inside a @FragmentScreen Fragment hosted by Gezgin, §11.1)"
    }
    return json.decodeFromString(PolymorphicSerializer(Route::class), encoded)
}
