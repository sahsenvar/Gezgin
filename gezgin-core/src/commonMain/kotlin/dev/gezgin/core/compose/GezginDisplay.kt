package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.RawNavigator

/**
 * Nav3 `NavDisplay` adapter'ı (§2.1/§4.2/§12) — `entries` trailing lambda'sı [GezginEntryScope]
 * alıcısında `register<R> { ... }` çağrılarını (veya Faz 3.4'ün üreteceği `provideXEntry`'leri)
 * toplar; içerik (`GezginKey` listesi) HER ZAMAN `navigator.keys`'ten okunur — `navigator.backStack`
 * yalnız TETİKLEYİCİ olarak `collectAsState()` edilir (public `Route` listesi recompose'u tetikler,
 * gerçek entry inşası internal `keys`'in taşıdığı `id`'lerden — R2 contentKey sözleşmesi bunu
 * gerektirir; `backStack`'in kendisi `id` taşımaz).
 *
 * **Kuruluş guard'ı (§12) — `rememberNavigator`'ın YAPAMADIĞI kısım:** kind bilgisi yalnız burada
 * (entry-scope registry'sinde) mevcut. Register'lar toplandıktan SONRA, kök (`navigator.keys.first()`)
 * route'un kayıtlı kind'ı `SCREEN` DIŞINDAYSA (`Dialog`/`BottomSheet`/`FullscreenModal`) kuruluş
 * `error()` ile durur — Nav3 `OverlayScene`'in `require(overlaidEntries.isNotEmpty())` invariant'ını
 * (bir modal'ın altında en az bir normal entry olmadan var olamayacağı) kökte tek başına bir modal
 * başlatarak ihlal etmeyi önler. Route HENÜZ kayıtlı değilse (kind lookup `null`) burada patlamaz —
 * o durumun daha açıklayıcı hatası [toNavEntry]'de (`route için entry kaydı yok`) zaten var. Faz 4'te
 * scene wiring gelince bu guard gevşetilecek (TODO).
 *
 * **Decorator'lar (kapsam kaydırması, bkz. task-3.2-report.md):** `rememberSaveableStateHolder` /
 * `rememberSavedState` / `rememberViewModelStore` `NavEntryDecorator`'ları bu görevde YOK. Task 3.0
 * spike'ının çıkardığı ortak `NavDisplay` parametre alt-kümesinde (android 1.1.4 ↔ JB desktop
 * alpha05) decorator alan bir parametre YOKTU (yalnız `entries`, `modifier`, `contentAlignment`,
 * `sizeTransform`, `transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`, `onBack`
 * ortak) — bu yüzden kapsam Faz 3.3'e kaydırıldı; BLOCKED değil.
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
    val stack by navigator.backStack.collectAsState()   // yalnız tetikleyici (KDoc'taki gerekçe)
    val entryList = remember(stack) {
        navigator.keys.map { key -> scope.toNavEntry(key, navigator) }
    }
    NavDisplay(
        entries = entryList,
        modifier = modifier,
        onBack = { navigator.back() },
    )
}
