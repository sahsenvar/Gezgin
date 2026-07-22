@file:OptIn(GezginInternalApi::class)

package dev.gezgin.test

import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * UI-less test API with a raw surface; generated code adds the typed `from<Source>()` accessors.
 * Thin delegation over `RawNavigator` for tests that don't want to stand up a display layer.
 *
 * @author @sahsenvar
 */
public class GezginTestNavigator(
  start: Route,
  topology: GezginTopology,
  onRootBack: () -> Unit = {},
) {
  /**
   * The underlying `RawNavigator`. Gated behind `GezginInternalApi` (): the generated `fromX()`
   * accessors resolve through it, but tests should prefer the typed delegates below and the
   * generated `fromX()` extensions. Opt in explicitly to reach the raw surface.
   */
  @GezginInternalApi
  public val raw: RawNavigator =
    RawNavigator(start = start, topology = topology, onRootBack = onRootBack)

  /** The current route stack, from root to top. */
  public val backStack: List<Route>
    get() = raw.backStack.value

  /** The route currently at the top of [backStack]. */
  public val current: Route
    get() = raw.current

  /** Pushes [route] onto the stack using the runtime navigator. */
  public fun navigate(route: Route): Unit = raw.navigate(route)

  /** Pops the current route or invokes the configured root-back callback at the root. */
  public fun back(): Unit = raw.back()

  /** Replaces the current route with [route]. */
  public fun replaceTo(route: Route): Unit = raw.replaceTo(route)

  /** Pop back to [target] (inclusive pops [target] too). Mirrors the runtime `@BackTo`. */
  public fun backTo(target: KClass<out Route>, inclusive: Boolean = false): Unit =
    raw.backTo(target, inclusive)

  /** Tear down the current flow with Canceled (root → onRootBack). Mirrors the runtime `@Quit`. */
  public fun quit(): Unit = raw.quit()

  /**
   * Tear down the enclosing ResultFlow, delivering [result] to the caller. Mirrors the runtime
   * `quitWith`.
   */
  public fun quitWith(result: Any?): Unit = raw.quitWith(result)

  /**
   * Tear down the current flow (Canceled), then navigate to [route]. Mirrors the runtime
   * `@QuitAndGoTo`.
   */
  public fun quitAndGoTo(route: Route): Unit = raw.quitAndGoTo(route)

  /**
   * Deliver [result] to the top pending-target entry and pop it (test counterpart of the typed
   * navigator's `backWithResult`). Named to match the runtime terminology, not `deliverResult`.
   */
  public fun backWithResult(result: Any?): Unit = raw.backWithResult(result)

  /**
   * Codegen hook for the generated `fromX()` extensions: the id of the NEAREST (top-most) entry of
   * [route] on the stack. Throws an explanatory error if there is none (the source was never
   * visited), rather than silently building the wrong navigator.
   */
  public fun entryIdOf(route: KClass<out Route>): Long =
    raw.entryIdOf(route)
      ?: error(
        "GezginTestNavigator.entryIdOf: no entry for ${route.simpleName} on the stack ($backStack)"
      )
}
