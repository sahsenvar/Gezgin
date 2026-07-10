package dev.gezgin.core.fragment

import androidx.fragment.app.Fragment
import dev.gezgin.core.Route
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty

/**
 * Task 6.2 — `@FragmentScreen` interop'un CANLI-referans (`gezginNav`) ve tipli-arg (`gezginArgs`) erişim
 * yarısı (spec §11.1, Task 6.0 §1 "I1" split). İki delege İKİ FARKLI kaynaktan okur:
 * - **`gezginArgs<Route>()` → Fragment'ın kendi `arguments` Bundle'ı** ([decodeGezginRoute]). `arguments`
 *   Fragment örneklenirken (`setArguments`, `onUpdate`'ten ÖNCE) kurulur → `onUpdate` zamanlamasından
 *   BAĞIMSIZ decode edilir (spec 291 "route Bundle'dan → PD-safe"; §B4). Route'un PD kaynağı Gezgin'in KENDİ
 *   backstack'i (§1d) olduğundan taze-process restore'da entry yeniden render → `arguments` taze route'la
 *   yeniden üretilir → decode çalışır.
 *
 *   **Geçerlilik penceresi (KESİN sözleşme):** `gezginArgs`, `onCreateView`/`onViewCreated` ve sonrasından
 *   itibaren HER durumda güvenle okunur. İLK-YARATIM instance'ında (`AndroidFragment` Fragment'ı KENDİ
 *   örnekliyor: ilk kompozisyon `route.toBundle(raw)`'ı Fragment yaratılmadan ÖNCE değerlendirir →
 *   [gezginFragmentJson] o an dolu) `onAttach`/`onCreate`'ten itibaren de güvenlidir. AMA taze-process
 *   FragmentManager-RESTORE branch'inde DEĞİL: gerçek process-death'ten sonra
 *   `FragmentActivity.onCreate(savedInstanceState)`, FM-saved Fragment'ı `setContent`'in ilk kompozisyonu
 *   (dolayısıyla YENİ process'te `route.toBundle`) çalışmadan ÖNCE geri yükleyip ona `onAttach`/`onCreate`
 *   dispatch eder → o an [gezginFragmentJson] hâlâ `null` ([gezginBoundRoute] açıklayıcı hata fırlatır). Bu
 *   yüzden `onAttach`/`onCreate`'te okuma SPESİFİK olarak bu restore branch'inde garanti DEĞİLDİR;
 *   `onCreateView`/`onViewCreated` (container yaratımı ilk kompozisyonun arkasında → `toBundle` her zaman önce
 *   çalışmıştır) her iki branch'te de güvenlidir. Örnek `HelpFragment` zaten `onViewCreated`'da okur.
 * - **`gezginNav<Navigator>()` → aşağıdaki [boundRegistry]** ([bindGezgin] `onUpdate`'te doldurur). Canlı bir
 *   navigator facade'ının serileştirilebilir biçimi YOKTUR → instance-anahtarlı registry'de taşınmalı,
 *   Bundle'da değil. Bu yüzden `gezginNav` gerçekten `onUpdate` çalışana dek geçersizdir (spec 298).
 */

/** Registry girdisi: bir `@FragmentScreen` Fragment'ının `gezginNav` ile eriştiği CANLI navigator facade.
 *  (Route BURADA tutulmaz — `gezginArgs` onu `arguments` Bundle'ından okur, §B4.) Navigator tipi üretilen
 *  `XNavigator` olduğundan gezgin-core onu bilmez → `Any?`; `gezginNav` reified tipe cast'ler. [nav] `null`
 *  ise route hiçbir navigator KAZANMAZ (edge'siz yaprak — hiç edge/back-edge/result-contract yok → üretilmiş
 *  bir `XNavigator` yok, §11.1): Fragment bind EDİLMİŞTİR ama `gezginNav` okunamaz ([gezginBoundNav] FS5
 *  fırlatır) — bu "henüz bind yok" (registry'de kayıt HİÇ yok) durumundan AYRIDIR. */
private class BoundGezgin(val nav: Any?)

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
public fun bindGezgin(fragment: Fragment, route: Route, nav: Any) {
    boundRegistry[fragment] = BoundGezgin(nav)
}

/**
 * [bindGezgin]'in NAV'SIZ aşırı-yüklemesi — route bir navigator KAZANMADIĞINDA (edge'siz yaprak: hiç
 * @GoTo/@ReplaceTo/@BackTo/... edge'i, result-contract'ı yok → [dev.gezgin.processor.codegen.NavigatorCodegen]
 * ona bir `XNavigator` ÜRETMEZ, §11.1). ÜRETİLEN `provideXEntry` bu durumda `nav`'ı hiç bağlamaz ve
 * `onUpdate = { fragment -> bindGezgin(fragment, route) }` çağırır (var olmayan bir factory'i ASLA çağırmaz —
 * SC2/MV7'nin bir faz sonraki karşılığı; koşul [dev.gezgin.processor.GezginProcessor]'da `NavigatorCodegen.
 * hasNavigator` ile hesaplanır). Fragment'ı navigator'sız BAĞLAR: `gezginArgs` (Bundle'dan) yine çalışır; ama
 * `gezginNav` okunursa [gezginBoundNav] `[FS5]` hatası fırlatır — "bind yok" değil, "navigator YOK". Meşru bir
 * görüntü-yalnızca (display-only) brownfield ekranı (Settings/About) bu yolu izler; KSP-zamanı REDDEDİLMEZ.
 *
 * **KOŞULSUZ put** — nav'lı aşırı-yükleme ile aynı idempotency sözleşmesi (bind-once guard YOK): config-
 * change/PD sonrası yeni instance yeniden bağlanmalı. `public`: üretilen kod tüketici modülünde.
 */
@Suppress("UNUSED_PARAMETER")
public fun bindGezgin(fragment: Fragment, route: Route) {
    boundRegistry[fragment] = BoundGezgin(nav = null)
}

/**
 * `gezginNav`'ın registry okuması + İKİ ayrı açıklayıcı hata (inline olmayan gerçek gövde; [gezginNav] yalnız
 * reified cast'i inline'lar → `boundRegistry`/`BoundGezgin` private kalabilir):
 * - registry'de kayıt HİÇ yoksa → "henüz bind edilmedi" (`onUpdate` daha çalışmadı).
 * - kayıt var ama `nav == null` ise → `[FS5]` "route'un navigator'ı YOK" (edge'siz yaprak, nav'sız
 *   [bindGezgin] ile bağlandı). Bu iki durum KASITEN ayrık mesajlarla ayrılır.
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
 * `@Screen`'in `nav` param'ının Fragment karşılığı (§11.1). `by gezginNav<XNavigator>()` — bind-sonrası canlı
 * navigator'ı reified tipe cast'leyerek döndürür. [gezginBoundNav] iki durumu ayrı hatalarla ayırır: henüz
 * bind edilmediyse "bind yok", route'un navigator'ı yoksa (edge'siz yaprak, nav'sız [bindGezgin] ile bağlandı)
 * `[FS5]` "navigator YOK". (Genelde ikincisi zaten derleme-zamanı yakalanır: navigator'ı olmayan bir route'un
 * `XNavigator` tipi hiç ÜRETİLMEZ → `gezginNav<XNavigator>()` çözümlenemez; FS5 kalıntı bir runtime ağıdır.)
 */
public inline fun <reified N> gezginNav(): ReadOnlyProperty<Fragment, N> =
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
 * `@Screen`'in `route` param'ının Fragment karşılığı (§11.1). `by gezginArgs<XRoute>()` — Fragment'ın
 * `arguments` Bundle'ından tipli route'u decode edip reified tipe cast'ler. `onUpdate`'ten BAĞIMSIZ (arguments
 * örnekleme anında kurulur). **Geçerlilik:** `onCreateView`/`onViewCreated` ve sonrasında HER durumda PD-safe;
 * `onAttach`/`onCreate`'te yalnız İLK-YARATIM instance'ında güvenli, taze-process FragmentManager-restore
 * branch'inde DEĞİL (o an [gezginFragmentJson] henüz `null` — bkz. dosya başı KDoc + [gezginBoundRoute]
 * hatası). Route'u `onCreateView`/`onViewCreated`'da oku (§B4).
 */
public inline fun <reified R : Route> gezginArgs(): ReadOnlyProperty<Fragment, R> =
    ReadOnlyProperty { fragment, _ -> gezginBoundRoute(fragment) as R }
