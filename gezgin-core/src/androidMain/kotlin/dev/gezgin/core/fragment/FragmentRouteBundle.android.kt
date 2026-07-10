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
 * Task 6.2 — `@FragmentScreen` interop'un route↔Bundle serileştirme yarısı (spec §11.1, Task 6.0 §3).
 *
 * Bir [Route], `arguments` Bundle'ına **polimorfik** olarak (`PolymorphicSerializer(Route::class)`)
 * kodlanır — bu, `GezginKey.route`'un ve backstack PD'sinin (`RawNavigator.save`/restore) route için ZATEN
 * kullandığı MEKANİZMANIN AYNISI; polimorfizm app'in `SerializersModule`'ünden gelir (Gezgin'in kendisinden
 * değil). Böylece Gezgin PD'sini atlatan HER route bunu da yapı-gereği atlatır (§3b). Yeni Json/modül YOK:
 * [nav]'ın (`internal val json`, artık modül-içi görünür) TAŞIDIĞI app-Json yeniden kullanılır.
 */

/** `@FragmentScreen` route'unun `arguments` Bundle'ında tek `String` extra olarak taşındığı sabit anahtar. */
private const val GEZGIN_FRAGMENT_ROUTE_KEY = "dev.gezgin.fragment.route"

/**
 * App'in polimorfik [Json]'ı (backstack PD'sini besleyen AYNI instance), `gezginArgs`'ın decode'u için
 * yakalanır. **Neden bir process-geneli tutucu (§B4 kararı):** `gezginArgs<Route>()` KULLANICININ çıplak
 * `Fragment` alt sınıfı içinde, kapsamda hiç `RawNavigator`/composition/DI OLMADAN çağrılır — dolayısıyla
 * `route.toBundle(nav)`'ın (ÜRETİLEN kod, `nav` kapsamda) yaptığı gibi `nav.json`'ı doğrudan okuyamaz.
 * [toBundle] bunu, `arguments =` değeri olarak `AndroidFragment`'ın Fragment'ı örneklemesinden KESİNLİKLE
 * ÖNCE değerlendiği için, tutucu her (taze-process dahil) kompozisyonda Fragment args'ını okumadan ÖNCE
 * dolar. App başına tek `gezginSerializersModule` (§3.3) → tek etkin Json; çok-NavDisplay'de bile route
 * polimorfizmi için eşdeğer. `@Volatile`: thread görünürlük sigortası (pratikte hep ana thread).
 */
@Volatile
internal var gezginFragmentJson: Json? = null

/**
 * [this] route'u [nav]'ın app-Json'ıyla polimorfik olarak tek `String` extra'ya kodlayıp Bundle döndürür.
 * ÜRETİLEN `provideXEntry` içinden (`arguments = route.toBundle(raw)`) çağrılır — kullanıcıya yönelik
 * doğrudan bir API DEĞİL. **`public` (dispatch'in `internal` önerisinin AKSİNE — zorunlu sapma, §B4/deviations):**
 * üretilen kod TÜKETİCİ modülünde yaşar, modüller-arası `internal` GÖRÜNMEZ; `register`/navigator-factory
 * gibi diğer codegen-çağrılı sembollerle aynı (public ama codegen-yönelimli). Yan etki: `gezginArgs`'ın
 * kapsamsız decode yolu için [gezginFragmentJson]'ı yakalar.
 */
@GezginInternalApi
public fun Route.toBundle(nav: RawNavigator): Bundle {
    gezginFragmentJson = nav.json
    val encoded = nav.json.encodeToString(PolymorphicSerializer(Route::class), this)
    return Bundle().apply { putString(GEZGIN_FRAGMENT_ROUTE_KEY, encoded) }
}

/**
 * [toBundle]'ın tersi — Bundle'daki polimorfik route'u [json] ile decode eder. `gezginArgs`'ın kapsamsız
 * decode yolu (`gezginBoundRoute` → yakalanan [gezginFragmentJson]) BUNU doğrudan çağırır; ayrı bir
 * `Bundle.toRoute(nav)` simetrik-inverse sarmalayıcısı YOKTUR — o yalnız `nav.json`'ı bu fonksiyona iletirdi,
 * hiçbir yerden çağrılmıyordu ve `Bundle` (Android) olduğu için Robolectric'siz test edilemezdi (Task 6.0'ın
 * "Robolectric yok" kararı). Serileştirme mekanizması yine `FragmentRouteSerializationTest`'te (commonTest,
 * `Bundle`'sız) polimorfik round-trip ile kanıtlanır.
 */
internal fun decodeGezginRoute(json: Json, bundle: Bundle): Route {
    val encoded = requireNotNull(bundle.getString(GEZGIN_FRAGMENT_ROUTE_KEY)) {
        "Bundle '$GEZGIN_FRAGMENT_ROUTE_KEY' taşımıyor — bu Bundle Gezgin route.toBundle() ile üretilmedi " +
            "(gezginArgs yalnız @FragmentScreen ile Gezgin'in host ettiği Fragment içinde geçerli, §11.1)"
    }
    return json.decodeFromString(PolymorphicSerializer(Route::class), encoded)
}
