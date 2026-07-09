package dev.gezgin.core.fragment

import androidx.fragment.app.Fragment
import dev.gezgin.core.Route
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty

/**
 * Task 6.2 — `@FragmentScreen` interop'un CANLI-referans (`gezginNav`) ve tipli-arg (`gezginArgs`) erişim
 * yarısı (spec §11.1, Task 6.0 §1 "I1" split). İki delege İKİ FARKLI kaynaktan okur:
 * - **`gezginArgs<Route>()` → Fragment'ın kendi `arguments` Bundle'ı** ([decodeGezginRoute]). `arguments`
 *   Fragment örneklenirken (`setArguments`, `onUpdate`'ten ÖNCE) kurulur → onAttach'tan itibaren okunabilir,
 *   `onUpdate` zamanlamasından BAĞIMSIZ (spec 291 "route Bundle'dan → PD-safe"; §B4). Route'un PD kaynağı
 *   Gezgin'in KENDİ backstack'i (§1d) olduğundan taze-process restore'da entry yeniden render → `arguments`
 *   taze route'la yeniden üretilir → decode çalışır.
 * - **`gezginNav<Navigator>()` → aşağıdaki [boundRegistry]** ([bindGezgin] `onUpdate`'te doldurur). Canlı bir
 *   navigator facade'ının serileştirilebilir biçimi YOKTUR → instance-anahtarlı registry'de taşınmalı,
 *   Bundle'da değil. Bu yüzden `gezginNav` gerçekten `onUpdate` çalışana dek geçersizdir (spec 298).
 */

/** Registry girdisi: bir `@FragmentScreen` Fragment'ının `gezginNav` ile eriştiği CANLI navigator facade.
 *  (Route BURADA tutulmaz — `gezginArgs` onu `arguments` Bundle'ından okur, §B4.) Navigator tipi üretilen
 *  `XNavigator` olduğundan gezgin-core onu bilmez → `Any`; `gezginNav` reified tipe cast'ler. */
private class BoundGezgin(val nav: Any)

/**
 * Fragment-instance → canlı navigator yan-tablosu. **Zayıf anahtar (`WeakHashMap`):** config-change/PD ile
 * yeniden yaratılan Fragment FARKLI bir instance'tır (yeni zayıf anahtar) → eski girdi GC'lenir, registry
 * doğal olarak canlı instance'ı izler (Task 6.0 §1c). Ana thread'de kurulur/okunur; senkronizasyon yok.
 */
private val boundRegistry = WeakHashMap<Fragment, BoundGezgin>()

/**
 * ÜRETİLEN kod içinden `AndroidFragment.onUpdate`'te CANLI Fragment instance'ı (Task 6.0 §1b) + route + onun
 * navigator'ı ile çağrılır (`onUpdate = { fragment -> bindGezgin(fragment, route, nav) }`). Canlı navigator'ı
 * instance altında kaydeder.
 *
 * **KOŞULSUZ put — bind-once/skip-if-present guard'ı YOK (Task 6.0 review, kritik):** `onUpdate` canlı instance
 * başına bir kez çalışır (§1c); config-change/PD sonrası YENİ instance için yeniden çalışması yeniden-bind
 * ETMELİ — "zaten bağlı, atla" savunması bu yolu KIRAN tek şeydir. Eşit veriyle üzerine yazmak zararsız
 * (idempotent-by-construction). `public`: üretilen kod TÜKETİCİ modülünde (modüller-arası `internal` görünmez).
 *
 * [route] şu an registry'ce kullanılmıyor (arg'lar Bundle'da taşınır, §B4) ama üretilen `onUpdate { bindGezgin
 * (fragment, route, nav) }` şekliyle bire bir olsun ve gelecekteki registry-tabanlı arg erişimine kapı açık
 * kalsın diye imzada TUTULUR.
 */
@Suppress("UNUSED_PARAMETER")
fun bindGezgin(fragment: Fragment, route: Route, nav: Any) {
    boundRegistry[fragment] = BoundGezgin(nav)
}

/**
 * `gezginNav`'ın registry okuması + bind-öncesi açıklayıcı hata (inline olmayan gerçek gövde; [gezginNav]
 * yalnız reified cast'i inline'lar → `boundRegistry`/`BoundGezgin` private kalabilir).
 */
@PublishedApi
internal fun gezginBoundNav(fragment: Fragment): Any =
    boundRegistry[fragment]?.nav ?: error(
        "gezginNav yalnız AndroidFragment'ın onUpdate'i bu Fragment instance'ını bind ettikten SONRA " +
            "okunabilir (bind = ilk onUpdate; §11.1 lifecycle sözleşmesi). ${fragment::class.simpleName} " +
            "için henüz bind yok — canlı navigator serileştirilemez, arguments'tan gelemez.",
    )

/**
 * `@Screen`'in `nav` param'ının Fragment karşılığı (§11.1). `by gezginNav<XNavigator>()` — bind-sonrası canlı
 * navigator'ı reified tipe cast'leyerek döndürür; bind-öncesi ([gezginBoundNav]) açıklayıcı hata fırlatır.
 */
inline fun <reified N> gezginNav(): ReadOnlyProperty<Fragment, N> =
    ReadOnlyProperty { fragment, _ -> gezginBoundNav(fragment) as N }

/**
 * `gezginArgs`'ın `arguments`-Bundle okuması (inline olmayan gerçek gövde; [gezginArgs] yalnız reified cast'i
 * inline'lar → [gezginFragmentJson]/[decodeGezginRoute] `internal` kalabilir). Bundle'dan, `onUpdate`'ten
 * BAĞIMSIZ decode eder (§B4) — [gezginFragmentJson] null ise (pratikte asla: `toBundle` Fragment yaratılmadan
 * önce doldurur) açıklayıcı hata.
 */
@PublishedApi
internal fun gezginBoundRoute(fragment: Fragment): Route {
    val json = gezginFragmentJson ?: error(
        "gezginArgs okunmadan önce hiç Gezgin route.toBundle() değerlendirilmemiş — @FragmentScreen'li " +
            "Fragment yalnız Gezgin'in (GezginDisplay + üretilen provideXEntry) host ettiği bir entry olarak " +
            "geçerlidir (§11.1).",
    )
    val bundle = fragment.arguments ?: error(
        "gezginArgs: ${fragment::class.simpleName}.arguments null — Fragment Gezgin route.toBundle() ile " +
            "kurulan arguments taşımıyor (yalnız @FragmentScreen host'unda geçerli, §11.1).",
    )
    return decodeGezginRoute(json, bundle)
}

/**
 * `@Screen`'in `route` param'ının Fragment karşılığı (§11.1). `by gezginArgs<XRoute>()` — Fragment'ın
 * `arguments` Bundle'ından tipli route'u decode edip reified tipe cast'ler. `onUpdate`'ten BAĞIMSIZ (arguments
 * örnekleme anında kurulur) → onAttach'tan itibaren PD-safe okunabilir (§B4).
 */
inline fun <reified R : Route> gezginArgs(): ReadOnlyProperty<Fragment, R> =
    ReadOnlyProperty { fragment, _ -> gezginBoundRoute(fragment) as R }
