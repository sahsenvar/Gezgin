package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Kind annotations (§3.2) — placed on a composable; they do three things: mark it as a destination,
 * declare its presentation kind, and bind it to a route. [route] is MANDATORY: it names the destination
 * route explicitly (e.g. `@Screen(FeedScreenRoute::class)`), so the composable↔route binding is visible
 * without reading generated code. Marks a plain full-screen destination.
 */
@Target(AnnotationTarget.FUNCTION)
@Repeatable
public annotation class Screen(val route: KClass<out Route>)

/**
 * Kind: renders the route as a modal **dialog** overlay (§3.2/§7) — the route may implement the optional
 * [dev.gezgin.core.DialogContract] for presentation properties. [route] is MANDATORY and names the
 * destination route explicitly (see [Screen]).
 *
 * Name-clash note: shares the simple name `Dialog` with `androidx.compose.ui.window.Dialog` (Compose's own
 * dialog composable) — if both are used in the same file, an import alias is recommended
 * (e.g. `import androidx.compose.ui.window.Dialog as ComposeDialog`); otherwise the compiler reports an
 * ambiguity, or (depending on import order) silently resolves the wrong one.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class Dialog(val route: KClass<out Route>)

/**
 * Kind: renders the route as a modal **bottom sheet** (§3.2/§7) — the route may implement the optional
 * [dev.gezgin.core.BottomSheetContract] for presentation properties; content can dismiss the sheet
 * programmatically via [dev.gezgin.core.compose.LocalGezginSheetController]. [route] is MANDATORY and
 * names the destination route explicitly (see [Screen]).
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class BottomSheet(val route: KClass<out Route>)

/**
 * Kind: renders the route as a **full-screen** modal overlay (§3.2/§7; no scrim, no
 * `usePlatformDefaultWidth` concept) — the route may implement the optional
 * [dev.gezgin.core.FullscreenModalContract] for presentation properties. [route] is MANDATORY and names
 * the destination route explicitly (see [Screen]).
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class FullscreenModal(val route: KClass<out Route>)
