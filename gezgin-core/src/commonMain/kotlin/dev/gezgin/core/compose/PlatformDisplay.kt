package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import dev.gezgin.core.Route

/** Platform decorators appended after the common saveable-state decorator by [GezginDisplay]. */
@Composable internal expect fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>>

/** Entry-scoped platform handler that consumes back for a non-root `@NoBack` destination. */
@Composable internal expect fun GezginNoBackHandler()

/**
 * Platform wrapper for incompatible Android and desktop `NavDisplay` scene APIs. Implementations
 * compose Gezgin dialog and sheet overlays with a single-pane fallback. [onBack] targets the live
 * top; [pinnedBack] targets the modal entry that owns a dismissal callback.
 */
@Composable
internal expect fun GezginNavDisplay(
  entries: List<NavEntry<Route>>,
  modifier: Modifier,
  onBack: () -> Unit,
  pinnedBack: (Long) -> Unit,
)
