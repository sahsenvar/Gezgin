package dev.gezgin.core.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
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
