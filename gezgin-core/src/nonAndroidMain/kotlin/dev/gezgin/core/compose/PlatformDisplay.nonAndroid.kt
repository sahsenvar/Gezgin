package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * Provides a per-entry `ViewModelStore`, matching Android entry ownership. Each stack entry
 * receives a child store that is cleared when popped, while covered or recomposed entries retain
 * their ViewModels. A remembered root owner is supplied explicitly because neither a desktop window
 * nor a `UIViewController` host need provide `LocalViewModelStoreOwner`; its root store is cleared
 * when the display leaves composition. [GezginDisplay] places this after the saveable-state
 * decorator.
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> {
  val storeOwner = remember { GezginWindowViewModelStoreOwner() }
  DisposableEffect(storeOwner) { onDispose { storeOwner.viewModelStore.clear() } }
  return listOf(rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = storeOwner))
}

/** Display-scoped root owner for the per-entry ViewModel stores. */
private class GezginWindowViewModelStoreOwner : ViewModelStoreOwner {
  override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * Installs no entry-scoped back handler. Desktop dispatches no system back at all, and on iOS the
 * edge-swipe reaches `NavDisplay`'s own navigation-event handler, whose `onBack` is [gezginOnBack]
 * — already the behavioural carrier of `@NoBack`. An entry-scoped handler would only suppress the
 * predictive preview animation that iOS starts before the guard refuses the pop.
 */
@Composable
internal actual fun GezginNoBackHandler() {
  /* No platform back handler is needed on desktop. */
}

/**
 * Uses ordered scene strategies: dialog and sheet overlays precede the single-pane fallback, and
 * each overlay pins dismissal to its owning entry. The built-in dialog strategy cannot preserve
 * that ownership.
 */
@Composable
internal actual fun GezginNavDisplay(
  entries: List<NavEntry<Route>>,
  modifier: Modifier,
  onBack: () -> Unit,
  pinnedBack: (Long) -> Unit,
) {
  // Keep the stateless strategy list stable across recompositions, as on Android.
  val sceneStrategies =
    remember(pinnedBack) {
      listOf(
        GezginDialogSceneStrategy(pinnedBack),
        GezginBottomSheetSceneStrategy(pinnedBack),
        SinglePaneSceneStrategy(),
      )
    }
  NavDisplay(
    entries = entries,
    modifier = modifier,
    sceneStrategies = sceneStrategies,
    onBack = onBack,
  )
}
