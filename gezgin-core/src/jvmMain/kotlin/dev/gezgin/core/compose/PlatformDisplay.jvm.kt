package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntryDecorator
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
