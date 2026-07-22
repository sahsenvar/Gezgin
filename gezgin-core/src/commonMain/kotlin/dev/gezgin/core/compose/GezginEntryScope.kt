package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * The presentation kind — the runtime counterpart of the `@Screen`/`@Dialog`/`@BottomSheet`/
 * `@FullscreenModal` annotations. `DIALOG` and `FULLSCREEN_MODAL` entries get DialogSceneStrategy
 * metadata in [toNavEntry] (properties from the optional DialogContract/FullscreenModalContract) →
 * `Dialog` overlay render (see EntryAdapter.kt); FULLSCREEN_MODAL uses
 * `usePlatformDefaultWidth=false` (fullscreen). `BOTTOM_SHEET` gets GezginBottomSheetSceneStrategy
 * metadata → `ModalBottomSheet` overlay (a hand-written OverlayScene, the sheet controller injected
 * via a Local); `SCREEN` is single-pane.
 *
 * @author @sahsenvar
 */
public enum class EntryKind {
  /** A regular single-pane screen entry. */
  SCREEN,

  /** A dialog overlay entry. */
  DIALOG,

  /** A modal bottom-sheet overlay entry. */
  BOTTOM_SHEET,

  /** A full-window modal overlay entry. */
  FULLSCREEN_MODAL,
}

/**
 * A Gezgin registry record — [kind] selects modal scene wiring, and [content] is the composable
 * content narrowed to `Route` (the safe cast in register<R>).
 *
 * [noBack] (M5′, ): the runtime counterpart of the `@NoBack` annotation. When `true` this entry is
 * TERMINAL — a Gezgin-owned back-swallower is set up: (1) the [gezginOnBack] guard turns `back()`
 * into a no-op while this entry is the top (no pop; except the root exemption), (2) [toNavEntry]
 * wraps the content with an entry-scoped back handler ([GezginNoBackHandler]) OUTER (BEFORE the
 * screen content — in the dispatcher LIFO the screen's own `BackHandler` registers more INNER/later
 * and wins). Generated entries read `@NoBack` and set this register-time flag.
 */
@PublishedApi
internal class RegisteredEntry(
  val kind: EntryKind,
  val noBack: Boolean,
  val content: @Composable (Route) -> Unit,
)

/**
 * The trailing-lambda receiver of `GezginDisplay` — the user (or codegen's generated
 * `provideXEntry`) calls [register] here to bind a route to its content. No wrapper type leaks to
 * the user: the registry is `internal`, read only by the [dev.gezgin.core.compose] package
 * (GezginDisplay/adapter).
 *
 * @author @sahsenvar
 */
public class GezginEntryScope internal constructor() {

  // Thread-safety note: NOT synchronized (`mutableMapOf`, plain) — deliberate, because it is
  // populated
  // ONLY ONCE, on the main/Compose thread, inside `GezginDisplay`'s `remember {
  // GezginEntryScope().apply
  // (entries) }` setup block; after setup it is read-only (no register calls). Multi-threaded
  // writes after
  // setup are neither supported nor expected.
  @PublishedApi
  internal val registry: MutableMap<KClass<out Route>, RegisteredEntry> = mutableMapOf()

  /**
   * Registers content for `R`. If the same `R` is registered twice it throws a descriptive error
   * (at register time, without waiting for the first render — a wrong setup blows up early).
   */
  public inline fun <reified R : Route> register(
    kind: EntryKind = EntryKind.SCREEN,
    noBack: Boolean = false,
    noinline content: @Composable (R) -> Unit,
  ) {
    val routeClass = R::class
    if (registry.containsKey(routeClass)) {
      error("Entry is already registered for route: ${routeClass.simpleName}")
    }
    @Suppress("UNCHECKED_CAST")
    registry[routeClass] = RegisteredEntry(kind, noBack) { route -> content(route as R) }
  }
}
