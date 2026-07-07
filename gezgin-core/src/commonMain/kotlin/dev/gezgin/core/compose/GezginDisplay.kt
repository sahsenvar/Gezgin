package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * Task 3.0 spike — Nav3 tiplerinin (JB `navigation3-ui` + Google `navigation3-runtime`) commonMain'de
 * çözülüp derlendiğini kanıtlayan minimal iskelet. Gerçek `NavDisplay` wiring'i (entryProvider,
 * decorators, transition cascade) Faz 3.2'de gelecek — bu dosya yalnız tip/API yüzeyini pinler:
 *  - `NavEntry<Route>(key, contentKey, metadata, content)` — Faz 3.1'de `contentKey = GezginKey.id`.
 *  - `NavDisplay(backStack: List<NavEntry<T>>, ...)` — entry-listesi alan overload referans alınır.
 */
@Composable
fun GezginDisplayPlaceholder(navigator: RawNavigator) {
    val stack: List<Route> by navigator.backStack.collectAsState()
    val entries = remember(stack) {
        stack.map { route ->
            NavEntry(key = route, contentKey = route) { key: Route ->
                // Faz 3.2'de gerçek entry içeriği (provideXEntry) buraya bağlanacak.
            }
        }
    }
    referenceNavDisplaySignature(entries)
}

/** Yalnızca `NavDisplay(List<NavEntry<T>>, ...)` overload'ının tip çözüldüğünü göstermek için private referans. */
@Composable
private fun referenceNavDisplaySignature(entries: List<NavEntry<Route>>) {
    NavDisplay(entries = entries, onBack = {})
}
