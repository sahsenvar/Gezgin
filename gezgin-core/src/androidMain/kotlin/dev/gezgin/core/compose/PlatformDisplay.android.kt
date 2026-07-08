package dev.gezgin.core.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * Android: per-entry `ViewModelStore` decorator'ı (host `ComponentActivity`
 * `LocalViewModelStoreOwner`'ı sağlar). Saveable-state-holder decorator'ından SONRA sıralanır
 * (o `LocalSavedStateRegistryOwner`'ı sağlar; VM store decorator'ı `SavedStateHandle` için ona
 * ihtiyaç duyar — [GezginDisplay] listeyi `[saveable] + platform` sırasıyla kurar).
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> =
    listOf(rememberViewModelStoreNavEntryDecorator())

/** Android: gerçek sistem-back tüketici (entry-scoped, OUTER). `enabled=true` → back yutulur (pop yok). */
@Composable
internal actual fun GezginNoBackHandler() {
    BackHandler(enabled = true) { /* @NoBack: back'i tüket — pop yok, preview başlamaz (M5′) */ }
}

/**
 * Android (Google 1.1.4): `NavDisplay` scene-strategy'si **ÇOĞUL** `sceneStrategies: List<SceneStrategy<T>>`.
 * `listOf(DialogSceneStrategy(), GezginBottomSheetSceneStrategy(), SinglePaneSceneStrategy())` — dialog-
 * ve sheet-metadata'lı entry'ler overlay, kalanı tek-pane (default `SinglePaneSceneStrategy` fallback'ı en
 * sonda açıkça korunur; her overlay stratejisi ilgili metadata key'i yoksa null döner).
 */
@Composable
internal actual fun GezginNavDisplay(
    entries: List<NavEntry<Route>>,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    NavDisplay(
        entries = entries,
        modifier = modifier,
        sceneStrategies = listOf(
            DialogSceneStrategy<Route>(),
            GezginBottomSheetSceneStrategy(),
            SinglePaneSceneStrategy(),
        ),
        onBack = onBack,
    )
}
