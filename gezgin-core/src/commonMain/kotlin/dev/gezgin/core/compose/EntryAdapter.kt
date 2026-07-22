@file:OptIn(GezginInternalApi::class, dev.gezgin.core.ExperimentalGezginMigrationApi::class)

package dev.gezgin.core.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.BottomSheetContract
import dev.gezgin.core.BottomSheetDragHandleMode
import dev.gezgin.core.DialogContract
import dev.gezgin.core.FullscreenModalContract
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * Converts an id-bearing [GezginKey] into a Navigation 3 entry. The unique id becomes the content
 * key, giving equal route values independent saved state and ViewModel ownership. Registration is
 * resolved before content runs, so missing routes fail immediately.
 *
 * Modal kinds receive Gezgin-owned dialog or sheet metadata whose dismissal is pinned to the entry.
 * Runtime contracts are checked against `@NoBack`, content receives its owning navigator locals,
 * and non-root `@NoBack` entries install an outer platform handler. Screen transitions are stored
 * per entry so pop and predictive navigation use the metadata of the scene being left.
 */
internal fun GezginEntryScope.toNavEntry(
  key: GezginKey,
  navigator: RawNavigator,
  transitions: GezginTransition?,
  isRoot: Boolean = false,
): NavEntry<Route> {
  val registered =
    registry[key.route::class]
      ?: error("No entry is registered for route: ${key.route::class.simpleName}")
  // Guard every dynamic path that could construct a modal-only root stack.
  require(!(isRoot && registered.kind != EntryKind.SCREEN)) {
    "GezginDisplay: modal kind (${registered.kind}) cannot be the only/root entry in the stack; " +
      "a modal must have at least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). " +
      "Do not place a modal at root with replaceTo/quitAndGoTo. route: ${key.route::class.simpleName}"
  }
  val installNoBack = registered.noBack && !isRoot
  // Gezgin-owned dialog and sheet keys preserve entry-pinned dismissal; the built-in dialog
  // strategy only exposes the display-wide back callback.
  val dialogProperties = resolveDialogProperties(registered.kind, key.route)
  val sheetProps = resolveBottomSheetProps(registered.kind, key.route)
  // A no-back modal must disable back dismissal; sheets must also disable drag gestures.
  if (registered.noBack) {
    if (dialogProperties != null)
      requireBackDismissCompatible(key.route, dialogProperties.dismissOnBackPress)
    if (sheetProps != null) {
      requireBackDismissCompatible(key.route, sheetProps.dismissOnBackPress)
      require(!sheetProps.sheetGesturesEnabled) {
        "Bottom sheet setup conflict (${key.route::class.simpleName}): @NoBack requires " +
          "sheetGesturesEnabled=false so user drag/swipe cannot hide the sheet while keeping its " +
          "route on the stack. dismissOnClickOutside is independent and is not part of this guard (§7)."
      }
    }
  }
  // Only screens receive transition metadata; modal metadata would animate the underlying scene.
  val transitionMetadata =
    if (registered.kind == EntryKind.SCREEN) {
      resolveTransition(key.route, transitions)?.toNavEntryMetadata().orEmpty()
    } else {
      emptyMap()
    }
  // Modal kinds are mutually exclusive and use distinct metadata keys.
  val metadata: Map<String, Any> = buildMap {
    putAll(transitionMetadata)
    if (dialogProperties != null) put(GEZGIN_DIALOG_KEY, dialogProperties)
    if (sheetProps != null) put(GEZGIN_BOTTOM_SHEET_KEY, sheetProps)
  }
  return NavEntry(key = key.route, contentKey = key.id, metadata = metadata) { route ->
    CompositionLocalProvider(
      LocalGezginEntryId provides key.id,
      LocalGezginRawNavigator provides navigator,
    ) {
      if (installNoBack) GezginNoBackHandler() // Register outside and before screen content.
      registered.content(route)
    }
  }
}

/** Resolves runtime dialog properties, using fullscreen width semantics for fullscreen modals. */
private fun resolveDialogProperties(kind: EntryKind, route: Route): DialogProperties? =
  when (kind) {
    EntryKind.DIALOG -> {
      val contract = route as? DialogContract
      DialogProperties(
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        usePlatformDefaultWidth = contract?.usePlatformDefaultWidth ?: true,
      )
    }
    EntryKind.FULLSCREEN_MODAL -> {
      val contract = route as? FullscreenModalContract
      DialogProperties(
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        usePlatformDefaultWidth = false, // Fullscreen modals always own the full width.
      )
    }
    EntryKind.SCREEN,
    EntryKind.BOTTOM_SHEET -> null
  }

/** Resolves runtime sheet properties or defaults for a bottom-sheet route. */
private fun resolveBottomSheetProps(kind: EntryKind, route: Route): GezginBottomSheetProps? =
  when (kind) {
    EntryKind.BOTTOM_SHEET -> {
      val contract = route as? BottomSheetContract
      GezginBottomSheetProps(
        skipPartiallyExpanded = contract?.skipPartiallyExpanded ?: false,
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        sheetGesturesEnabled = contract?.sheetGesturesEnabled ?: true,
        dragHandleMode = contract?.dragHandleMode ?: BottomSheetDragHandleMode.Default,
      )
    }
    EntryKind.SCREEN,
    EntryKind.DIALOG,
    EntryKind.FULLSCREEN_MODAL -> null
  }

/** Rejects the runtime contradiction between `@NoBack` and back-driven modal dismissal. */
private fun requireBackDismissCompatible(route: Route, dismissOnBackPress: Boolean) {
  require(!dismissOnBackPress) {
    "Modal setup conflict (${route::class.simpleName}): @NoBack (back is swallowed) and " +
      "dismissOnBackPress=true (back dismisses the modal) cannot be used together. In the modal " +
      "DialogContract/FullscreenModalContract/BottomSheetContract, set " +
      "`override val dismissOnBackPress = false`, or remove @NoBack (§7)."
  }
}
