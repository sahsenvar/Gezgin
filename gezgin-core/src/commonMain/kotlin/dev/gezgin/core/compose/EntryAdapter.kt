package dev.gezgin.core.compose

import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.GezginKey
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
 */
internal fun GezginEntryScope.toNavEntry(key: GezginKey): NavEntry<Route> {
    val registered = registry[key.route::class]
        ?: error("route için entry kaydı yok: ${key.route::class.simpleName}")
    return NavEntry(key = key.route, contentKey = key.id) { route -> registered.content(route) }
}
