package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.RawNavigator

/**
 * Top-entry-drive contract (§10.1/§12): an entry's content only ever sees a navigator built with its OWN
 * `entryId` — [GezginDisplay] wraps every entry content with these two locals inside `toNavEntry`, and the
 * generated `provideXEntry` reads them to build the typed navigator (`xNavigator()`).
 *
 * Both locals are gated behind [GezginInternalApi] (K4): only generated code installs and reads them.
 * Application code that needs read access to the active navigator (core-mode) uses [currentGezginNavigator]
 * instead — a read-only accessor that can't rewrite the display's provider contract.
 *
 * `staticCompositionLocalOf` — the value is stable per entry (unchanged on recomposition; a different
 * entry installs a fresh `Content()` tree), so the read-tracking cost of a dynamic local is unnecessary.
 */
@GezginInternalApi
public val LocalGezginEntryId: ProvidableCompositionLocal<Long> = staticCompositionLocalOf<Long> {
    error("LocalGezginEntryId yalnız GezginDisplay'in kurduğu entry content'leri içinde okunabilir.")
}

@GezginInternalApi
public val LocalGezginRawNavigator: ProvidableCompositionLocal<RawNavigator> = staticCompositionLocalOf<RawNavigator> {
    error("LocalGezginRawNavigator yalnız GezginDisplay'in kurduğu entry content'leri içinde okunabilir.")
}

/**
 * Read-only access to the [RawNavigator] driving the current entry — the sanctioned way for core-mode
 * (`register<R> { … }`) content to reach the raw navigator without the writable [LocalGezginRawNavigator]
 * provider (which is [GezginInternalApi]). Valid only inside entry content installed by [GezginDisplay].
 */
@OptIn(GezginInternalApi::class)
@Composable
public fun currentGezginNavigator(): RawNavigator = LocalGezginRawNavigator.current
