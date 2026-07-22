@file:OptIn(ExperimentalMaterial3Api::class, dev.gezgin.core.ExperimentalGezginMigrationApi::class)

package dev.gezgin.core.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.gezgin.core.BottomSheetDragHandleMode
import dev.gezgin.core.Route

/**
 * Gezgin-owned handle a `@BottomSheet` content uses to dismiss its sheet — read from
 * [LocalGezginSheetController]. It replaces the raw material3 `SheetState` on the public surface,
 * so no experimental third-party type is locked into Gezgin's ABI; the material `SheetState` stays
 * an internal implementation detail of [GezginBottomSheetScene].
 *
 * A bare visual [hide] on its own leaves the entry on the stack (the back button would then dismiss
 * an invisible sheet) — pair it with a navigator pop, or use [hideAndBack]. For a `ResultRoute`
 * sheet, deliver the result via the typed navigator's `backWithResult(value)` right after [hide].
 *
 * @author @sahsenvar
 */
public interface GezginSheetController {
  /** Animate the sheet to fully hidden WITHOUT popping the entry. Pair with a navigator pop. */
  public suspend fun hide()

  /**
   * Animate the sheet hidden, then pop this sheet entry via the navigator (Canceled for a
   * ResultRoute sheet).
   */
  public suspend fun hideAndBack()
}

/**
 * The [GezginSheetController] Gezgin injects into `@BottomSheet` content. Symmetric to
 * [LocalGezginEntryId]/[LocalGezginRawNavigator]: [GezginBottomSheetScene] provides it around
 * `entry.Content()`. Gated behind [dev.gezgin.core.GezginInternalApi] is unnecessary here — the
 * controller is Gezgin-owned and safe to read; only reading it OUTSIDE a `@BottomSheet` content is
 * an error.
 *
 * `staticCompositionLocalOf` — the value is stable per sheet entry (the controller is
 * `remember`ed), so the read-tracking cost of a dynamic local is unnecessary.
 */
public val LocalGezginSheetController: ProvidableCompositionLocal<GezginSheetController> =
  staticCompositionLocalOf {
    error(
      "LocalGezginSheetController can only be read inside @BottomSheet content installed by GezginDisplay."
    )
  }

/**
 * [GezginSheetController] backed by the material3 `SheetState` + the scene's back callback
 * (internal impl).
 */
private class MaterialSheetController(
  private val sheetState: SheetState,
  private val onBack: () -> Unit,
) : GezginSheetController {
  override suspend fun hide() {
    sheetState.hide()
  }

  override suspend fun hideAndBack() {
    sheetState.hide()
    onBack()
  }
}

/**
 * Metadata key used to carry [GezginBottomSheetScene] properties on both platforms. It is distinct
 * from the dialog and transition metadata keys.
 */
internal const val GEZGIN_BOTTOM_SHEET_KEY = "gezginBottomSheet"

/**
 * Sheet properties resolved from the route's optional [dev.gezgin.core.BottomSheetContract] runtime
 * value. They are stored under [GEZGIN_BOTTOM_SHEET_KEY], and the scene strategy uses them to
 * construct [GezginBottomSheetScene].
 */
internal data class GezginBottomSheetProps(
  val skipPartiallyExpanded: Boolean,
  val dismissOnBackPress: Boolean,
  val dismissOnClickOutside: Boolean,
  val sheetGesturesEnabled: Boolean,
  val dragHandleMode: BottomSheetDragHandleMode,
)

/**
 * Bottom-sheet `OverlayScene` implemented with Material3 `ModalBottomSheet`. [overlaidEntries]
 * keeps the underlying screen visible, while [content] renders the sheet and provides
 * [LocalGezginSheetController] around the entry content.
 *
 * **Dismissal ownership:** the strategy supplies [onBack] as an entry-pinned
 * `navigator.back(entryId)` callback. Material3 routes swipe-down, scrim taps, and system back to
 * `onDismissRequest`. The callback pops the sheet, producing `Canceled` for a `ResultRoute`, only
 * while that sheet remains on top; a late or duplicate dismissal is a no-op and cannot pop the
 * underlying screen.
 *
 * Nav3 scene reuse depends on equality, so this class compares the same structural fields used by
 * its hash code.
 *
 * Gesture dismissal animates before `onDismissRequest`. A direct programmatic `navigator.back()`
 * removes the entry immediately, so it does not run the Material sheet hide animation.
 */
internal class GezginBottomSheetScene(
  override val key: Any,
  private val entry: NavEntry<Route>,
  override val overlaidEntries: List<NavEntry<Route>>,
  private val props: GezginBottomSheetProps,
  private val onBack: () -> Unit,
) : OverlayScene<Route> {

  init {
    // Match the overlay invariant enforced by DialogScene: every overlay needs an underlying entry.
    require(overlaidEntries.isNotEmpty()) {
      "GezginBottomSheetScene: overlaidEntries cannot be empty; a BottomSheet overlay must have at " +
        "least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). key: $key"
    }
  }

  override val entries: List<NavEntry<Route>> = listOf(entry)
  override val previousEntries: List<NavEntry<Route>> = overlaidEntries

  override val content: @Composable () -> Unit = {
    val sheetState =
      rememberModalBottomSheetState(skipPartiallyExpanded = props.skipPartiallyExpanded)
    val controller = remember(sheetState) { MaterialSheetController(sheetState, onBack) }
    val properties =
      ModalBottomSheetProperties(
        shouldDismissOnBackPress = props.dismissOnBackPress,
        shouldDismissOnClickOutside = props.dismissOnClickOutside,
      )
    when (props.dragHandleMode) {
      BottomSheetDragHandleMode.Default ->
        ModalBottomSheet(
          onDismissRequest = onBack,
          sheetState = sheetState,
          sheetGesturesEnabled = props.sheetGesturesEnabled,
          properties = properties,
        ) {
          CompositionLocalProvider(LocalGezginSheetController provides controller) {
            entry.Content()
          }
        }
      BottomSheetDragHandleMode.None ->
        ModalBottomSheet(
          onDismissRequest = onBack,
          sheetState = sheetState,
          sheetGesturesEnabled = props.sheetGesturesEnabled,
          dragHandle = null,
          properties = properties,
        ) {
          CompositionLocalProvider(LocalGezginSheetController provides controller) {
            entry.Content()
          }
        }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false
    other as GezginBottomSheetScene
    return key == other.key &&
      entry == other.entry &&
      overlaidEntries == other.overlaidEntries &&
      props == other.props
  }

  override fun hashCode(): Int {
    // Use the same field order and set as equals.
    var result = key.hashCode()
    result = 31 * result + entry.hashCode()
    result = 31 * result + overlaidEntries.hashCode()
    result = 31 * result + props.hashCode()
    return result
  }

  override fun toString(): String =
    "GezginBottomSheetScene(key=$key, entry=$entry, overlaidEntries=$overlaidEntries, props=$props)"
}

/**
 * BottomSheet `SceneStrategy` — top entry'nin `NavEntry.metadata`'sında [GEZGIN_BOTTOM_SHEET_KEY]
 * (adapter [toNavEntry] `kind == BOTTOM_SHEET` iken yazar) varsa bir [GezginBottomSheetScene]
 * döndürür, yoksa `null` (zincirdeki sonraki stratejiye devret). [GezginDialogSceneStrategy]
 * deseni; `overlaidEntries = entries.dropLast(1)` (arka SCREEN görünür). Dismiss, [onDismiss] ile
 * sahip-entry id'sine (`lastEntry.contentKey`) pinlenir → sheet artık top değilken pop no-op.
 * [GezginNavDisplay] actual'larında GezginDialogSceneStrategy'nin YANINA (fallback
 * `SinglePaneSceneStrategy`'den ÖNCE) eklenir.
 */
internal class GezginBottomSheetSceneStrategy(private val onDismiss: (Long) -> Unit) :
  SceneStrategy<Route> {
  override fun SceneStrategyScope<Route>.calculateScene(
    entries: List<NavEntry<Route>>
  ): Scene<Route>? {
    val lastEntry = entries.lastOrNull()
    val props = lastEntry?.metadata?.get(GEZGIN_BOTTOM_SHEET_KEY) as? GezginBottomSheetProps
    return props?.let {
      val entryId = lastEntry.contentKey as Long
      GezginBottomSheetScene(
        key = lastEntry.contentKey,
        entry = lastEntry,
        overlaidEntries = entries.dropLast(1),
        props = it,
        onBack = { onDismiss(entryId) }, // Keep dismissal pinned to the owning entry.
      )
    }
  }
}
