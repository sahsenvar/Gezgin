package dev.gezgin.core.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

private data class AndroidNavDisplayKey(val contentKey: Any, val occurrence: Int) : Route

internal data class AndroidNavDisplayState(
  val backStack: List<Route>,
  val entriesByKey: Map<Route, NavEntry<Route>>,
)

/**
 * Navigation 3 1.0.0's `NavDisplay` has no `entries` overload. Preserve each already-decorated
 * entry under a private route key while leaving its opaque `contentKey`, metadata, and content
 * intact.
 */
internal fun adaptAndroidNavDisplayEntries(entries: List<NavEntry<Route>>): AndroidNavDisplayState {
  val adaptedEntries =
    entries.mapIndexed { occurrence, entry ->
      val key: Route = AndroidNavDisplayKey(entry.contentKey, occurrence)
      key to
        NavEntry(key = key, contentKey = entry.contentKey, metadata = entry.metadata) {
          entry.Content()
        }
    }
  return AndroidNavDisplayState(
    backStack = adaptedEntries.map { it.first },
    entriesByKey = adaptedEntries.toMap(),
  )
}

/**
 * Android: per-entry `ViewModelStore` decorator'ı (host `ComponentActivity`
 * `LocalViewModelStoreOwner`'ı sağlar). Saveable-state-holder decorator'ından SONRA sıralanır (o
 * `LocalSavedStateRegistryOwner`'ı sağlar; VM store decorator'ı `SavedStateHandle` için ona ihtiyaç
 * duyar — [GezginDisplay] listeyi `[saveable] + platform` sırasıyla kurar).
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> =
  listOf(rememberViewModelStoreNavEntryDecorator())

/**
 * Android: gerçek sistem-back tüketici (entry-scoped, OUTER). `enabled=true` → back yutulur (pop
 * yok).
 */
@Composable
internal actual fun GezginNoBackHandler() {
  BackHandler(enabled = true) { /* @NoBack: back'i tüket — pop yok, preview başlamaz (M5′) */ }
}

/**
 * Android (Google 1.0.0): `NavDisplay` ham bir back stack ve entry provider alır. Gezgin'in önceki
 * katmanda oluşturduğu/decorate ettiği entry'ler, decoration tekrar uygulanmadan stabil özel route
 * anahtarlarıyla bu API'ye uyarlanır. Scene strategy zinciri `GezginDialogSceneStrategy(pinnedBack)
 * then GezginBottomSheetSceneStrategy(pinnedBack) then SinglePaneSceneStrategy()` — dialog- ve
 * sheet-metadata'lı entry'ler overlay (dismiss'i sahip-entry'ye pinli), kalanı tek-pane (default
 * `SinglePaneSceneStrategy` fallback'ı en sonda açıkça korunur; her overlay stratejisi ilgili
 * metadata key'i yoksa null döner). Nav3 built-in `DialogSceneStrategy` BIRAKILDI (entry-pin
 * edilemezdi).
 */
@Composable
internal actual fun GezginNavDisplay(
  entries: List<NavEntry<Route>>,
  modifier: Modifier,
  onBack: () -> Unit,
  pinnedBack: (Long) -> Unit,
) {
  // strateji instance'ları/list'i stateless ama HER recomposition'da yeniden kurulmasın
  // (GezginDisplay'in decorator/onBack için uyguladığı kimlik-stabilizasyonuyla tutarlı):
  // `remember`.
  // `pinnedBack` GezginDisplay'de navigator'a `remember`'lı → stabil anahtar.
  val sceneStrategy =
    remember(pinnedBack) {
      GezginDialogSceneStrategy(pinnedBack) then
        GezginBottomSheetSceneStrategy(pinnedBack) then
        SinglePaneSceneStrategy()
    }
  // Navigation 3 1.0.0 has no `entries` overload. Entries arrive here already decorated by
  // GezginDisplay, so wrap their content under stable Route keys and explicitly disable Nav3's
  // default decorator to preserve the original per-entry state and ViewModel ownership.
  val navDisplayState = remember(entries) { adaptAndroidNavDisplayEntries(entries) }
  NavDisplay(
    backStack = navDisplayState.backStack,
    modifier = modifier,
    entryDecorators = emptyList(),
    sceneStrategy = sceneStrategy,
    onBack = onBack,
    entryProvider = { key -> navDisplayState.entriesByKey.getValue(key) },
  )
}
