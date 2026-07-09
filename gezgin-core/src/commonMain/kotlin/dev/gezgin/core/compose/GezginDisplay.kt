package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * Nav3 `NavDisplay` adapter'ı (§2.1/§4.2/§12) — `entries` trailing lambda'sı [GezginEntryScope]
 * alıcısında `register<R> { ... }` çağrılarını (veya Faz 3.4'ün üreteceği `provideXEntry`'leri)
 * toplar; içerik (`GezginKey` listesi) HER ZAMAN `navigator.keysState`'ten (`id` taşır) okunur —
 * `backStack` (public `Route`, id'siz) DEĞİL: `StateFlow` eşit-değer dedup'ı yüzünden `replaceTo`
 * ile aynı-değer-farklı-id bir hedefe geçiş `backStack`'te emit üretmez, [keysState][RawNavigator.keysState]
 * ise `id` farkıyla yeni liste yayar (R2 contentKey sözleşmesi; Task 3.3 deliverable 4a).
 *
 * **Decorator'lar (Task 3.3 deliverable 1):** entries `NavDisplay`'e verilmeden ÖNCE
 * [rememberDecoratedNavEntries] ile decorate edilir. ORTAK: `rememberSaveableStateHolderNavEntryDecorator()`
 * (saveable state = zorunlu; `rememberSaveable` her entry'de kendi `contentKey`=id slot'unda çalışır →
 * eşit-değerli iki route AYRI saved state; R2 saved-state tarafı, desktop dahil). PLATFORM:
 * [rememberPlatformEntryDecorators] — Android'de `rememberViewModelStoreNavEntryDecorator()` (per-entry
 * VM store; `LocalViewModelStoreOwner` host Activity'den), desktop'ta boş (owner garanti değil). Sıra
 * `[saveable] + platform`: VM store decorator'ı saveable'ın sağladığı `SavedStateRegistryOwner`'a
 * bağımlı → saveable önce/OUTER.
 *
 * **Kuruluş guard'ı (§12) — `rememberNavigator`'ın YAPAMADIĞI kısım:** kind bilgisi yalnız burada
 * (entry-scope registry'sinde) mevcut. Register'lar toplandıktan SONRA, kök (`navigator.keys.first()`)
 * route'un kayıtlı kind'ı `SCREEN` DIŞINDAYSA (`Dialog`/`BottomSheet`/`FullscreenModal`) kuruluş
 * `error()` ile durur — Nav3 `OverlayScene`'in `require(overlaidEntries.isNotEmpty())` invariant'ını
 * (bir modal'ın altında en az bir normal entry olmadan var olamayacağı) kökte tek başına bir modal
 * başlatarak ihlal etmeyi önler. Route HENÜZ kayıtlı değilse (kind lookup `null`) burada patlamaz —
 * o durumun daha açıklayıcı hatası [toNavEntry]'de (`route için entry kaydı yok`) zaten var. Faz 4
 * scene wiring'i geldi ([GezginNavDisplay]/DialogSceneStrategy) AMA bu guard KALIR (gevşetilmez):
 * `OverlayScene` `require(overlaidEntries.isNotEmpty())` invariant'ı gerçektir — bir modal genuinely
 * kökte tek başına var olamaz (altında overlaid bir normal entry şart). Global Constraints §7.
 *
 * **Transition cascade (Task 3.5, §9) — PER-ENTRY metadata:** her entry kurulurken ([toNavEntry]) KENDİ
 * route'unun cascade'i çözülür ([resolveTransition]: `route.transition ?: transitions` —
 * screen>graph basamağı [Route.transition]'ın interface-override zincirinden BEDAVA gelir) ve
 * `NavEntry.metadata`'ya Nav3'ün PUBLIC `NavDisplay.transitionSpec/popTransitionSpec/
 * predictivePopTransitionSpec` sarmalayıcılarıyla yazılır ([GezginTransition.toNavEntryMetadata]).
 * Decompile bulgusu (review düzeltmesi — ilk "iki API tip-uyumsuz" tespiti YANLIŞTI): bu üç sarmalayıcı
 * HER İKİ target'ta da (desktop alpha05 + android 1.1.4) AYNI commonMain dosyasında, AYNI
 * `Map<String, Any>`-dönen imzayla PUBLIC; yalnız map anahtarının İÇ temsili farklı (alpha05: String
 * sabiti, 1.1.4: `NavMetadataKey.toString()`) — anahtar hep sarmalayıcıdan üretildiği için platform-içi
 * tutarlı. NavDisplay'in AnimatedContent çözümü `Scene.metadata`'yı (default = SON entry'nin metadata'sı)
 * NavDisplay-seviyesi parametrelerden ÖNCE okur — pop B→A'da çıkılan scene'in (B'nin) `popTransitionSpec`
 * metadata'sı kullanılır, yani "en içteki (screen) kazanır" POP yönünde de doğru (top-route'tan
 * NavDisplay-parametresi çözen ilk yaklaşım tam bu yüzden geri alındı: pop'ta A'nın spec'i okunuyordu).
 * `NavDisplay`'in transition parametreleri artık HİÇ geçilmiyor — cascade tamamen null ise (route yok,
 * graph yok, app default yok → metadata boş) Nav3 kendi `defaultTransitionSpec` ailesine düşer.
 * Predictive fallback (`predictive` yazılmazsa = backward, §9) metadata üretiminde uygulanır.
 *
 * **`entries` lambda'sı yalnız İLK composition'da yakalanır** (`remember { GezginEntryScope().apply(entries) }`
 * — `entries` sonraki recomposition'larda BİR DAHA çağrılmaz): içindeki `register<R> { ... }` çağrıları
 * koşulsuz olmalı (bir `if`/state'e bağlı koşullu register beklenen şekilde çalışmaz — kayıt kuruluşta
 * donar, sonradan değişmez).
 */
@Composable
fun GezginDisplay(
    navigator: RawNavigator,
    modifier: Modifier = Modifier,
    transitions: GezginTransition? = null,
    entries: GezginEntryScope.() -> Unit,
) {
    val scope = remember {
        GezginEntryScope().apply(entries).also { registered ->
            val rootRoute = navigator.keys.first().route
            val rootKind = registered.registry[rootRoute::class]?.kind
            // NOT: Bu YALNIZ start route'u kapsayan erken/redundant güvenlik ağıdır. ASIL modal-kind-at-root
            // guard'ı [toNavEntry]'dedir (her entry kurulurken `isRoot` ile) — o TÜM dinamik yolları da
            // (replaceTo/quitAndGoTo ile modal'ı köke koyma) kapatır. Bu check yine de erken/açık kalır.
            require(rootKind == null || rootKind == EntryKind.SCREEN) {
                "GezginDisplay: start route modal kind olamaz (kind=$rootKind) — §12 kuruluş " +
                    "guard'ı (KALICI: modal genuinely root OLAMAZ — OverlayScene ≥1 underlaid entry " +
                    "ister, §7). route: ${rootRoute::class.simpleName}"
            }
            // M4 — kind-lookup kancasını navigator'a enjekte et: replaceTo (clearUpTo=root) MUTASYONDAN
            // ÖNCE, sonuçtaki kök modal olacaksa reddedebilsin. Kind yalnız burada (registry) bilinir;
            // kayıtsız route → `false` (modal değil varsay; kayıtsızlığın açık hatası toNavEntry'de).
            navigator.modalRootGuard = { route ->
                registered.registry[route::class]?.kind?.let { it != EntryKind.SCREEN } ?: false
            }
        }
    }
    val keys by navigator.keysState.collectAsState()   // id taşır → id-only değişim de recompose eder (4a)
    // Transition cascade PER-ENTRY metadata'yla iner (bkz. dosya başı KDoc) — `transitions` remember
    // anahtarı: app-seviyesi default değişirse entry metadata'ları yeniden kurulmalı.
    val entryList = remember(keys, transitions) {
        keys.map { key -> scope.toNavEntry(key, navigator, transitions, isRoot = isRootEntry(keys, key.id)) }
    }
    // `remember` ile sabitlenmiş kimlik (Important 3, final-review): decorator @Composable'ları
    // ([rememberSaveableStateHolderNavEntryDecorator]/[rememberPlatformEntryDecorators]) her ikisi de
    // KENDİ içeriklerini `remember`'lasa da, bu ikisini birleştiren `listOf(...) + ...` HER
    // recomposition'da taze bir `List` instance'ı üretiyordu — `rememberDecoratedNavEntries`'e her
    // seferinde "değişti" görünen bir liste geçiyordu (referans-eşitliği yoksa cache miss). Anahtarlar
    // decorator'ların kendileri: onlar stabilse (normal durum — navigator/scope her recomposition'da
    // değişmez) bu liste artık stabil kalır.
    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<Route>()
    val platformDecorators = rememberPlatformEntryDecorators()
    val decorators: List<NavEntryDecorator<Route>> = remember(saveableDecorator, platformDecorators) {
        listOf(saveableDecorator) + platformDecorators
    }
    val decoratedEntries = rememberDecoratedNavEntries(entryList, decorators)
    // `gezginOnBack(navigator, scope)` de bir kurucu fonksiyon çağrısı — SABİT `navigator`/`scope`
    // (aynı composition boyunca) için `remember` olmadan her recomposition'da yeni bir lambda instance'ı
    // üretiyordu (NavDisplay'in `onBack` parametresi identity ile karşılaştırılabilir call-site'lar
    // için gereksiz iş). Davranış AYNI — sadece kimlik stabilize edildi.
    val onBack = remember(navigator, scope) { gezginOnBack(navigator, scope) }
    // Faz 4 scene wiring: `NavDisplay` çağrısı platform-özel sarmalayıcıda ([GezginNavDisplay]) —
    // sceneStrategy imzası android/desktop uzlaşmaz (expect/actual, bkz. PlatformDisplay.kt KDoc).
    // DialogSceneStrategy dialog-metadata'lı entry'yi ([toNavEntry], kind==DIALOG) overlay render eder.
    GezginNavDisplay(
        entries = decoratedEntries,
        modifier = modifier,
        onBack = onBack,
    )
}

/**
 * [NavDisplay] `onBack` lambda'sı — `@NoBack` runtime guard'ı (M5′, §4.2; Task 3.3 deliverable 3).
 * Geri çağrıldığında CANLI top entry'yi (`navigator.keys.last()`, capture edilmiş stale değer değil)
 * okur: top'un kaydı `noBack==true` VE top kök DEĞİLSE geri YUTULUR (pop yok) — aksi halde
 * `navigator.back()`. **Kök muafiyeti:** stack tek entry'liyken (`keys.size <= 1`) `noBack` yok sayılır
 * → `back()` (dipteyken `onRootBack`; kullanıcı app'e hapsolmaz).
 *
 * Desktop'ta `@NoBack`'in DAVRANIŞSAL taşıyıcısı budur (sistem-back/predictive yok, [GezginNoBackHandler]
 * desktop'ta no-op). Android'de ayrıca gerçek entry-scoped [GezginNoBackHandler] kurulur; bu guard orada
 * da bir emniyet ağı olarak `NavDisplay`'in kendi `onBack`'inde back'i yutar. `@Composable` DEĞİL →
 * Compose kurulumu olmadan birim/uiTest ile pinlenebilir.
 */
internal fun gezginOnBack(navigator: RawNavigator, scope: GezginEntryScope): () -> Unit = {
    val keys = navigator.keys
    val top = keys.lastOrNull()
    val isRoot = top == null || isRootEntry(keys, top.id)
    val topNoBack = top != null && scope.registry[top.route::class]?.noBack == true
    if (topNoBack && !isRoot) {
        // @NoBack top entry (kök değil): Gezgin-sahipli geri-yutma — pop YOK (M5′).
    } else {
        navigator.back()
    }
}

/**
 * Task 3.4 devri — [GezginDisplay]'in per-entry `isRoot` (`key.id == keys.first().id`) ile
 * [gezginOnBack]'in eski `keys.size <= 1` predicate'i AYNI şeyi soruyordu (stack'in dibindeki entry
 * TOP iken ikisi de tek entry'de eşitti — `size<=1` ⟺ top.id == first.id): tek yardımcıya birleştirildi.
 */
private fun isRootEntry(keys: List<GezginKey>, entryId: Long): Boolean = keys.firstOrNull()?.id == entryId
