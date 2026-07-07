package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
 * o durumun daha açıklayıcı hatası [toNavEntry]'de (`route için entry kaydı yok`) zaten var. Faz 4'te
 * scene wiring gelince bu guard gevşetilecek (TODO).
 */
@Composable
fun GezginDisplay(
    navigator: RawNavigator,
    modifier: Modifier = Modifier,
    entries: GezginEntryScope.() -> Unit,
) {
    val scope = remember {
        GezginEntryScope().apply(entries).also { registered ->
            val rootRoute = navigator.keys.first().route
            val rootKind = registered.registry[rootRoute::class]?.kind
            require(rootKind == null || rootKind == EntryKind.SCREEN) {
                "GezginDisplay: start route modal kind olamaz (kind=$rootKind) — §12 kuruluş " +
                    "guard'ı (Faz 4'te scene gelince gevşetilecek). route: ${rootRoute::class.simpleName}"
            }
        }
    }
    val keys by navigator.keysState.collectAsState()   // id taşır → id-only değişim de recompose eder (4a)
    val entryList = remember(keys) {
        val rootId = keys.firstOrNull()?.id
        keys.map { key -> scope.toNavEntry(key, navigator, isRoot = key.id == rootId) }
    }
    val decorators: List<NavEntryDecorator<Route>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator<Route>()) + rememberPlatformEntryDecorators()
    val decoratedEntries = rememberDecoratedNavEntries(entryList, decorators)
    NavDisplay(
        entries = decoratedEntries,
        modifier = modifier,
        onBack = gezginOnBack(navigator, scope),
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
    val isRoot = keys.size <= 1
    val topNoBack = top != null && scope.registry[top.route::class]?.noBack == true
    if (topNoBack && !isRoot) {
        // @NoBack top entry (kök değil): Gezgin-sahipli geri-yutma — pop YOK (M5′).
    } else {
        navigator.back()
    }
}
