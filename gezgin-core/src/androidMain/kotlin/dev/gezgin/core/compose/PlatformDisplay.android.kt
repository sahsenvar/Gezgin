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
 * Provides Android's per-entry `ViewModelStore` decorator. [GezginDisplay] places it after the
 * saveable-state decorator because `SavedStateHandle` creation needs that registry owner.
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> =
  listOf(rememberViewModelStoreNavEntryDecorator())

/** Consumes Android system back for an entry without popping it or starting predictive back. */
@Composable
internal actual fun GezginNoBackHandler() {
  BackHandler(enabled = true) { /* Consume back without popping or starting predictive preview. */ }
}

/**
 * Adapts decorated Gezgin entries to Android Navigation 3's back-stack/provider API. Dialog and
 * sheet strategies run before the single-pane fallback and pin dismissal to the owning entry. The
 * built-in dialog strategy is unsuitable because it cannot preserve that ownership.
 */
@Composable
internal actual fun GezginNavDisplay(
  entries: List<NavEntry<Route>>,
  modifier: Modifier,
  onBack: () -> Unit,
  pinnedBack: (Long) -> Unit,
) {
  // Keep the stateless strategy chain stable across recompositions. GezginDisplay also remembers
  // pinnedBack, making it a stable key.
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
