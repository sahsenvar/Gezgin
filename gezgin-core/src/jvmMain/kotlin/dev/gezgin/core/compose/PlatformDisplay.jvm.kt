package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * Desktop (JVM): platform-özel decorator YOK. `rememberViewModelStoreNavEntryDecorator()` çağrı anında
 * `checkNotNull(LocalViewModelStoreOwner.current)` yapar; CMP desktop host'unda (özellikle
 * `runComposeUiTest`) bu owner garanti değil → boş liste. Saveable-state-holder decorator'ı
 * (commonMain, [GezginDisplay]) desktop'ta zaten çalışır ve R2'nin saved-state tarafını karşılar.
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> = emptyList()

/**
 * Desktop (JVM): sistem-back/predictive-back kavramı yok → no-op. `@NoBack` geri-yutma davranışı
 * [gezginOnBack] guard'ıyla sağlanır (top `noBack` entry iken `onBack` pop yapmaz).
 */
@Composable
internal actual fun GezginNoBackHandler() { /* no-op — bkz. KDoc */ }

/**
 * Desktop (JB alpha05): `NavDisplay` scene-strategy'si **TEKİL** `sceneStrategy: SceneStrategy<T>`.
 * `DialogSceneStrategy() then GezginBottomSheetSceneStrategy() then SinglePaneSceneStrategy()` — dialog-
 * ve sheet-metadata'lı entry'ler overlay olarak (her strateji kendi metadata key'ini okur, ayrık), kalanı
 * tek-pane (SceneStrategy.then overlay-önce sözleşmesi; iki overlay stratejisi karşılıklı-dışlayan, sıra
 * önemsiz — SinglePane en sonda fallback).
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
        sceneStrategy = DialogSceneStrategy<Route>() then GezginBottomSheetSceneStrategy() then
            SinglePaneSceneStrategy(),
        onBack = onBack,
    )
}
