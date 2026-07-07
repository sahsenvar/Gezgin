package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Sunum kind'ı (§3.2) — `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal` annotation'larının
 * runtime karşılığı. Modal scene wiring (`DIALOG`/`BOTTOM_SHEET`/`FULLSCREEN_MODAL`) Faz 4'te;
 * bu fazda yalnız registry'de metadata olarak taşınır — [toNavEntry] hepsini normal (SCREEN gibi)
 * render eder (bkz. EntryAdapter.kt TODO).
 */
enum class EntryKind { SCREEN, DIALOG, BOTTOM_SHEET, FULLSCREEN_MODAL }

/**
 * Gezgin registry kaydı — [kind] Faz 4 modal scene wiring'i için metadata, [content] `Route`'a
 * daraltılmış (register<R>'daki güvenli cast) composable içerik.
 *
 * [noBack] (M5′, §4.2): `@NoBack` annotation'ının runtime karşılığı. `true` iken bu entry TERMINAL'dir
 * — Gezgin-sahipli bir geri-yutucu kurulur: (1) [gezginOnBack] guard'ı top bu entry iken `back()`'i
 * no-op'a çevirir (pop yok; kök-muafiyeti hariç), (2) [toNavEntry] content'i entry-scoped bir
 * geri-handler ([GezginNoBackHandler]) ile OUTER (ekran içeriğinden ÖNCE — dispatcher LIFO'sunda
 * ekranın kendi `BackHandler`'ı daha İÇ/sonra kaydolup kazanır) sarar. Faz 3.4 codegen'i `@NoBack`
 * okuyup bu flag'i doldurur; bu fazda register-zamanı parametresi olarak plumbing + test edilir.
 */
@PublishedApi
internal class RegisteredEntry(
    val kind: EntryKind,
    val noBack: Boolean,
    val content: @Composable (Route) -> Unit,
)

/**
 * `GezginDisplay`'in trailing lambda receiver'ı (§10.1/§12) — kullanıcı (veya codegen ürettiği
 * `provideXEntry`) burada [register] çağırarak route'u içeriğe bağlar. Kullanıcıya wrapper tip
 * sızmaz: registry `internal`, yalnız [dev.gezgin.core.compose] paketi (GezginDisplay/adapter) okur.
 */
class GezginEntryScope internal constructor() {

    @PublishedApi
    internal val registry: MutableMap<KClass<out Route>, RegisteredEntry> = mutableMapOf()

    /**
     * `R` için içerik kaydeder. Aynı `R` iki kez kaydedilirse açıklayıcı hata fırlatır (kayıt anında,
     * ilk render'a kadar beklemeden — yanlış kurulum erken patlar).
     */
    inline fun <reified R : Route> register(
        kind: EntryKind = EntryKind.SCREEN,
        noBack: Boolean = false,
        noinline content: @Composable (R) -> Unit,
    ) {
        val routeClass = R::class
        if (registry.containsKey(routeClass)) {
            error("Route için entry zaten kayıtlı: ${routeClass.simpleName}")
        }
        @Suppress("UNCHECKED_CAST")
        registry[routeClass] = RegisteredEntry(kind, noBack) { route -> content(route as R) }
    }
}
