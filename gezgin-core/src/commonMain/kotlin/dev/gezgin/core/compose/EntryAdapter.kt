package dev.gezgin.core.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.DialogSceneStrategy
import dev.gezgin.core.DialogContract
import dev.gezgin.core.FullscreenModalContract
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * `GezginKey` → Nav3 `NavEntry` adapter'ı (R2, §2.1): `contentKey = key.id` — instance kimliği,
 * aynı route iki kez push edilse bile Nav3'e **ayrı** decorator state (VM store/saved state)
 * kazandırır. `key = key.route` (kullanıcı route'u; Nav3 `NavDisplay`'in kendi backstack diff'i
 * için kullanılır — GezginKey zarfı kullanıcıya/Nav3'e hiç sızmaz, yalnız burada unwrap edilir).
 *
 * Lookup (registry'de route için kayıt var mı) **çağrı anında** yapılır — content lambda'sının
 * İÇİNDE değil. Kayıtsız route derlenmiş bir stack'e karışmışsa hata composable invoke edilmeden,
 * entry kurulurken patlar (erken/açık başarısızlık; sessiz boş ekran yok).
 *
 * Kind (`RegisteredEntry.kind`) → scene metadata (Faz 4, §7): `DIALOG`/`FULLSCREEN_MODAL` iken
 * `NavEntry.metadata`'ya `DialogSceneStrategy.dialog(properties)` işareti yazılır → [GezginNavDisplay]'e
 * bağlı DialogSceneStrategy o entry'yi `Dialog` overlay'inde (arka görünür) render eder. `properties`
 * route'un opsiyonel [DialogContract]/[FullscreenModalContract]'ından (route-instance runtime değeri,
 * §2.4) okunur; route implement etmemişse tip-bazlı varsayılan `DialogProperties` (FULLSCREEN_MODAL'da
 * `usePlatformDefaultWidth=false` = tam-ekran) kurulur. `BOTTOM_SHEET` hâlâ plain (4.2 çözer).
 *
 * **Guard — `dismissOnBackPress + @NoBack` çelişkisi (§7, kuruluş-zamanı RUNTIME):** `@NoBack` geri'yi
 * YUTAR ([gezginOnBack]/[GezginNoBackHandler]) ama `dismissOnBackPress=true` "geri dialog'u kapatsın"
 * der — ikisi bir arada tezat. `dismissOnBackPress` runtime değer (KSP okuyamaz) → derleme yerine
 * entry kuruluşunda `require` ile reddedilir (spec §7 "derleme hatası" bu yüzden runtime guard'a
 * çevrildi — bkz. §7 metni + 4.1 raporu).
 *
 * Task 3.2 devri — **top-entry-drive** (§10.1/§12): [navigator] parametresi eklendi (additive,
 * Task 3.1'in tek-parametreli imzasının yerini alır); content [LocalGezginEntryId]/
 * [LocalGezginRawNavigator] ile sarılır — bir entry'nin içeriği YALNIZ kendi `key.id`'siyle kurulmuş
 * navigator'ı Local'den okuyabilir (Faz 3.4 `provideXEntry` bunlardan tipli navigator kuracak).
 *
 * Task 3.3 — **`@NoBack` entry-scoped handler** (M5′, §4.2): kayıt `noBack==true` ve entry KÖK DEĞİLSE
 * ([isRoot]==false — "root entry'de noBack yok sayılır → back = onRootBack"), content ekran
 * içeriğinden ÖNCE [GezginNoBackHandler] ile sarılır: Gezgin'in handler'ı OUTER/önce kaydolur →
 * dispatcher LIFO'sunda ekranın kendi (daha İÇ, sonra kaydolan) `BackHandler`'ı kazanır, yoksa
 * Gezgin'inki back'i yutar. [isRoot] çağıran ([GezginDisplay]) tarafından stack'in dibi (`keys.first`)
 * bilgisinden geçirilir — call-time gerçeği, capture edilmiş stale scope değil (§10.1 staleness notu).
 *
 * Task 3.5 fix — **per-entry transition metadata (§9):** entry'nin KENDİ route'unun cascade'i
 * ([resolveTransition]: route-override > graph-mirası > app-default) çözülür ve `NavEntry.metadata`'ya
 * ([GezginTransition.toNavEntryMetadata] — Nav3'ün PUBLIC `NavDisplay.transitionSpec/popTransitionSpec/
 * predictivePopTransitionSpec` sarmalayıcılarıyla) yazılır. Top-route'tan NavDisplay-parametresi çözen
 * ilk yaklaşım GERİ ALINDI: pop B→A'da NavDisplay'in top'u artık A olduğundan B'nin `backward{}`/
 * `predictive{}` spec'leri hiç kullanılmıyordu (§9 "en içteki kazanır" ihlali). Per-entry metadata'da
 * Nav3'ün kendi AnimatedContent çözümü doğru entry'nin spec'ini seçer (`Scene.metadata` default'u = son
 * entry'nin metadata'sı; pop/predictive'de çıkılan scene'inki okunur — bkz. GezginDisplay KDoc).
 */
internal fun GezginEntryScope.toNavEntry(
    key: GezginKey,
    navigator: RawNavigator,
    transitions: GezginTransition?,
    isRoot: Boolean = false,
): NavEntry<Route> {
    val registered = registry[key.route::class]
        ?: error("route için entry kaydı yok: ${key.route::class.simpleName}")
    val installNoBack = registered.noBack && !isRoot
    // Faz 4 scene wiring (§7): transition metadata (per-entry, §9) + DIALOG/FULLSCREEN_MODAL ise
    // dialog-scene işareti. `DialogSceneStrategy.dialog(props)` public API iki platformda AYNI imzalı
    // (`Map<String, Any>` döner) — içteki metadata-key temsili farklı (android typed `NavMetadataKey`,
    // desktop string sabit) ama her platform kendi DialogSceneStrategy'siyle okur → platform-içi
    // tutarlı, commonMain'den güvenli çağrı. Anahtar çakışması yok (dialog anahtarı transition
    // anahtarlarından ayrık). BOTTOM_SHEET hâlâ plain (4.2 çözer).
    val dialogProperties = resolveDialogProperties(registered.kind, key.route)
    if (dialogProperties != null && registered.noBack) {
        // §7 guard (kuruluş-zamanı runtime): @NoBack geri'yi yutar; dismissOnBackPress geri'yle kapat der.
        require(!dialogProperties.dismissOnBackPress) {
            "Modal kuruluş çelişkisi (${key.route::class.simpleName}): @NoBack (geri yutulur) ile " +
                "dismissOnBackPress=true (geri modal'ı kapatır) birlikte olamaz. Modal " +
                "DialogContract/FullscreenModalContract'ında `override val dismissOnBackPress = false` " +
                "ver ya da @NoBack'i kaldır (§7)."
        }
    }
    val transitionMetadata = resolveTransition(key.route, transitions)?.toNavEntryMetadata().orEmpty()
    val metadata = if (dialogProperties != null) {
        transitionMetadata + DialogSceneStrategy.dialog(dialogProperties)
    } else {
        transitionMetadata
    }
    return NavEntry(key = key.route, contentKey = key.id, metadata = metadata) { route ->
        CompositionLocalProvider(
            LocalGezginEntryId provides key.id,
            LocalGezginRawNavigator provides navigator,
        ) {
            if (installNoBack) GezginNoBackHandler()   // OUTER: ekran içeriğinden önce kaydol (M5′)
            registered.content(route)
        }
    }
}

/**
 * Kind + route-instance → dialog scene [DialogProperties] (§7), yoksa `null` (dialog-dışı entry →
 * plain tek-pane). Property'ler route'un opsiyonel [DialogContract]/[FullscreenModalContract]'ından
 * (runtime değer, §2.4) okunur; route implement etmemişse tip-varsayılan `DialogProperties`:
 * - [EntryKind.DIALOG] → `DialogProperties(dismissOnBackPress, dismissOnClickOutside,
 *   usePlatformDefaultWidth)` — [DialogContract]'tan ya da (yoksa) tüm-default.
 * - [EntryKind.FULLSCREEN_MODAL] → `usePlatformDefaultWidth = false` (tam-ekran = SABİT tanım),
 *   dismiss'ler [FullscreenModalContract]'tan ya da (yoksa) default `true`.
 * - diğerleri → `null`.
 */
private fun resolveDialogProperties(kind: EntryKind, route: Route): DialogProperties? = when (kind) {
    EntryKind.DIALOG -> {
        val contract = route as? DialogContract
        DialogProperties(
            dismissOnBackPress = contract?.dismissOnBackPress ?: true,
            dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
            usePlatformDefaultWidth = contract?.usePlatformDefaultWidth ?: true,
        )
    }
    EntryKind.FULLSCREEN_MODAL -> {
        val contract = route as? FullscreenModalContract
        DialogProperties(
            dismissOnBackPress = contract?.dismissOnBackPress ?: true,
            dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
            usePlatformDefaultWidth = false,   // tam-ekran modal tanımı
        )
    }
    EntryKind.SCREEN, EntryKind.BOTTOM_SHEET -> null
}
