package dev.gezgin.core

/**
 * The **optional** typed home for modal presentation properties (§7) — a `@Dialog` route implements this
 * interface to carry the dialog window's dismiss/layout behavior as a **runtime value** off the route
 * instance (NOT from KSP — §2.4; the adapter reads it via `route as? DialogContract`). If the route does
 * not implement it, the adapter uses the default [androidx.compose.ui.window.DialogProperties] (identical
 * to `DialogContract`'s defaults).
 *
 * This is NOT a **marker** like `ResultRoute<T>`: it carries PROPERTIES. Two ways to supply them — **use
 * the `get() =` form in BOTH** (see the m5 warning below):
 * - **CONSTANT** (route-specific, no args): `override val dismissOnClickOutside get() = false` — an
 *   interface property override (defaulted → write only what you want to change).
 * - **CONDITIONAL** (depends on the call site): supply from a route ctor param —
 *   `data class Confirm(val cancelable: Boolean) : ..., DialogContract {
 *        override val dismissOnClickOutside get() = cancelable }`. The route is already `@Serializable` →
 *   no extra work, the param is serialized (PD-safe).
 *
 * **OPEN-TIME CONSTANT (not live state):** these getters are read ONCE when the entry is built and captured
 * into an immutable `DialogProperties`; they are NOT re-read while the dialog is open. So the getter must
 * depend only on the ROUTE INSTANCE (ctor params / constants) — a getter reading changing UI state (e.g.
 * `get() = someTextField.isNotBlank()`) will NOT make the dialog's dismiss behavior react as that state
 * changes; the value is frozen at the value it had when the dialog opened.
 *
 * **m5 — `get() =` is REQUIRED (do NOT write an initializer `val`):** writing
 * `override val dismissOnClickOutside = false` (a backing-field initializer) makes kotlinx.serialization
 * include this property in the `@Serializable` route's SERIALIZED SCHEMA → the presentation prop leaks into
 * the saved-state (PD) format. With `encodeDefaults=false` the actual data may be harmless, but the schema
 * is polluted and it is unnecessary; the `get() =` form produces no backing field (getter-only) and never
 * enters the schema. So use the getter form even in the CONSTANT case.
 *
 * The fields map **one-to-one** to the real fields of [androidx.compose.ui.window.DialogProperties] (in the
 * adapter, [dev.gezgin.core.compose.toNavEntry]):
 * - [dismissOnBackPress] → `DialogProperties.dismissOnBackPress` (back button/Esc dismisses the dialog).
 * - [dismissOnClickOutside] → `DialogProperties.dismissOnClickOutside` (tapping outside dismisses).
 * - [usePlatformDefaultWidth] → `DialogProperties.usePlatformDefaultWidth`. The concrete counterpart of
 *   §7's abstract `layout` property: `true` = platform-default dialog width (wrapped), `false` = the content
 *   decides its own width (wide/near-fullscreen). Using this name instead of `layout` avoids hiding which
 *   `DialogProperties` field it maps down to (minimal magic).
 *
 * dismiss (tap-outside/Esc/when back is allowed) → the dialog scene's `onDismissRequest = onBack` →
 * `navigator.back()` → pop; if the route is a `ResultRoute`, the caller receives `Canceled` (the existing
 * `back()` path).
 */
public interface DialogContract {
    public val dismissOnClickOutside: Boolean get() = true
    public val dismissOnBackPress: Boolean get() = true
    public val usePlatformDefaultWidth: Boolean get() = true
}

/**
 * The optional typed home for fullscreen-modal presentation properties (§7) — the parallel of
 * [DialogContract], but with NO `usePlatformDefaultWidth`: a fullscreen modal BY DEFINITION decides its own
 * content width (`DialogProperties(usePlatformDefaultWidth = false)` — FIXED in the adapter). A
 * `@FullscreenModal` route carries only dismiss behavior. If the route does not implement it, the adapter
 * builds a fullscreen `DialogProperties` with the default dismisses (both `true`). **Overrides must use the
 * `get() =` form** ([DialogContract]'s m5 warning — an initializer `val` leaks into the serialized schema).
 *
 * NOTE: end-to-end on-device verification of §7 fullscreen-modal render (scrim, predictive) is in Task 4.3.
 * 4.1 sets up contract reading + metadata wiring + the guard; DialogSceneStrategy renders it as a fullscreen
 * dialog (usePlatformDefaultWidth=false; 4.0 report §6).
 */
public interface FullscreenModalContract {
    public val dismissOnClickOutside: Boolean get() = true
    public val dismissOnBackPress: Boolean get() = true
}

/**
 * The **optional** typed home for BottomSheet presentation properties (§7) — a `@BottomSheet` route
 * implements this interface to carry the modal sheet's behavior as a **runtime value** off the route
 * instance (same pattern as [DialogContract]; the adapter reads it via `route as? BottomSheetContract`). If
 * the route does not implement it, the adapter uses the type defaults (identical to the defaults below).
 * Overrides must use the `get() =` form ([DialogContract]'s m5 warning — an initializer `val` leaks into the
 * serialized schema).
 *
 * **Prop set — four fields mapping to the REAL knobs of material3 `ModalBottomSheet`** (the dismiss pair
 * symmetric with [DialogContract] + sheet-specific state/gesture knobs):
 * - [skipPartiallyExpanded] → `rememberModalBottomSheetState(skipPartiallyExpanded = ...)`. When `true` the
 *   sheet skips the intermediate (half-expanded) stop; it goes straight to fully expanded or hidden (for
 *   short-content sheets).
 * - [dismissOnBackPress] → `ModalBottomSheetProperties(shouldDismissOnBackPress = ...)`. When `false` the
 *   back button does not dismiss the sheet. Parallel to [DialogContract.dismissOnBackPress]; it is subject
 *   to the **same `@NoBack` guard** (`require(!(noBack && dismissOnBackPress))` in the adapter — a
 *   setup-time runtime check, §7).
 * - [dismissOnClickOutside] → `ModalBottomSheetProperties(shouldDismissOnClickOutside = ...)`. Whether a
 *   scrim-tap (outside tap) dismisses the sheet; default `true`. `false` → tap-outside does not dismiss, but
 *   swipe-down and the back button (if allowed) STILL work. Parallel to [DialogContract.dismissOnClickOutside].
 * - [sheetGesturesEnabled] → `ModalBottomSheet(sheetGesturesEnabled = ...)`. Whether user drag/swipe
 *   gestures can move or dismiss the sheet; default `true`. This is independent from outside dismissal.
 *   For a `@NoBack` sheet, both [dismissOnBackPress] and this property must be `false` so user back/swipe
 *   cannot hide the sheet while its route remains on the stack. Programmatic navigator back and
 *   [dev.gezgin.core.compose.GezginSheetController.hideAndBack] remain available.
 *
 * dismiss (swipe-down / scrim-tap / when back is allowed) → the sheet's `onDismissRequest = onBack` →
 * `navigator.back()` → pop; if the route is a `ResultRoute`, the caller receives `Canceled` (the existing
 * `back()` path — in material3 swipe+scrim+back ALL THREE funnel into a single `onDismissRequest`,
 * jar-verified).
 */
public interface BottomSheetContract {
    public val skipPartiallyExpanded: Boolean get() = false
    public val dismissOnBackPress: Boolean get() = true
    public val dismissOnClickOutside: Boolean get() = true
    public val sheetGesturesEnabled: Boolean get() = true
}
