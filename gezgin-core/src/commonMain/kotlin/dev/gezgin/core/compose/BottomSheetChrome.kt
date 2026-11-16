package dev.gezgin.core.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * App-wide visual chrome of every `@BottomSheet` overlay rendered by Gezgin. The consumer provides
 * it once through [LocalGezginBottomSheetChrome] above `GezginDisplay`, so the sheet look (floating
 * margins, corner shape, container color, drag handle, scrim) lives in one place instead of every
 * sheet content or route repeating it.
 *
 * Every field defaults to material3 `ModalBottomSheet`'s own default, so the default instance
 * renders exactly the 1.0.0 sheet.
 * - [modifier] is applied to the sheet surface; outer padding here yields a floating sheet (a gap
 *   to the sides and the bottom of the window).
 * - [shape] / [containerColor] replace the material defaults when non-null.
 * - [showDragHandle] `false` hides the built-in handle for every sheet. A route's
 *   [dev.gezgin.core.BottomSheetDragHandleMode.None] still hides it when this is `true`.
 * - [contentWindowInsets] replaces the material default insets applied inside the sheet when
 *   non-null; a floating sheet usually consumes the navigation bar in [modifier] and passes empty
 *   insets here.
 * - [scrimColor] replaces material's scrim (32% `colorScheme.scrim`) behind every sheet when
 *   non-null.
 *
 * Instances are compared by identity, so keep one instance per app (for example a top-level `val`
 * or a `remember`ed value).
 *
 * @author @sahsenvar
 */
@Immutable
public class GezginBottomSheetChrome(
  /** Modifier applied to the sheet surface. */
  public val modifier: Modifier = Modifier,
  /** Sheet surface shape; `null` keeps the material default. */
  public val shape: Shape? = null,
  /** Sheet surface color; `null` keeps the material default. */
  public val containerColor: Color? = null,
  /** Whether the built-in drag handle is drawn for sheets whose route does not hide it. */
  public val showDragHandle: Boolean = true,
  /** Insets applied inside the sheet; `null` keeps the material default. */
  public val contentWindowInsets: (@Composable () -> WindowInsets)? = null,
  /** Scrim drawn behind the sheet; `null` keeps the material default. */
  public val scrimColor: Color? = null,
)

/**
 * The [GezginBottomSheetChrome] every Gezgin bottom-sheet overlay reads. Defaults to the material
 * look; provide a custom value above `GezginDisplay` to restyle all sheets at once.
 */
public val LocalGezginBottomSheetChrome: ProvidableCompositionLocal<GezginBottomSheetChrome> =
  staticCompositionLocalOf {
    GezginBottomSheetChrome()
  }
