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
 * Provides a per-entry `ViewModelStore` on iOS, matching Android entry ownership. Each stack entry
 * receives a child store that is cleared when popped, while covered or recomposed entries retain
 * their ViewModels. A remembered window owner is supplied explicitly because a `UIViewController`
 * host need not provide `LocalViewModelStoreOwner`; its root store is cleared when the display
 * leaves composition. [GezginDisplay] places this after the saveable-state decorator.
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> {
  val storeOwner = remember { GezginWindowViewModelStoreOwner() }
  DisposableEffect(storeOwner) { onDispose { storeOwner.viewModelStore.clear() } }
  return listOf(rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = storeOwner))
}

/** Scene-scoped root owner for the per-entry iOS ViewModel stores. */
private class GezginWindowViewModelStoreOwner : ViewModelStoreOwner {
  override val viewModelStore: ViewModelStore = ViewModelStore()
}

/** No platform back handler is installed on iOS; [gezginOnBack] enforces `@NoBack` instead. */
@Composable
internal actual fun GezginNoBackHandler() {
  /* The back-gesture binding lands with the iOS back contract. */
}

/**
 * Uses ordered iOS scene strategies: dialog and sheet overlays precede the single-pane fallback,
 * and each overlay pins dismissal to its owning entry. The built-in dialog strategy cannot preserve
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
