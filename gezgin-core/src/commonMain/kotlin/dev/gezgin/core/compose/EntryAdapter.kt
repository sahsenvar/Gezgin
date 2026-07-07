package dev.gezgin.core.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavEntry
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
 * Kind (`RegisteredEntry.kind`) bu fazda `metadata`'ya yazılmaz — SCREEN dışı kind'lar da (Dialog/
 * BottomSheet/FullscreenModal) burada normal (tam ekran gibi) render edilir. Modal scene wiring
 * (`NavDisplay` `sceneStrategy`/metadata) Faz 4 TODO'su.
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
 */
internal fun GezginEntryScope.toNavEntry(
    key: GezginKey,
    navigator: RawNavigator,
    isRoot: Boolean = false,
): NavEntry<Route> {
    val registered = registry[key.route::class]
        ?: error("route için entry kaydı yok: ${key.route::class.simpleName}")
    val installNoBack = registered.noBack && !isRoot
    return NavEntry(key = key.route, contentKey = key.id) { route ->
        CompositionLocalProvider(
            LocalGezginEntryId provides key.id,
            LocalGezginRawNavigator provides navigator,
        ) {
            if (installNoBack) GezginNoBackHandler()   // OUTER: ekran içeriğinden önce kaydol (M5′)
            registered.content(route)
        }
    }
}
