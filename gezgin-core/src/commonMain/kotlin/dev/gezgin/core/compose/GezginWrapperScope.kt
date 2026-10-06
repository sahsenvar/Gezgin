package dev.gezgin.core.compose

import dev.gezgin.core.Route

/**
 * What Gezgin hands a `@ScreenWrapper` function: the entry's route, its compile-time metadata and a
 * little live back-stack state. A wrapper is declared as an extension on this type:
 * ```
 * @ScreenWrapper
 * @Composable
 * fun <S, I> GezginWrapperScope.AppScreenRoot(
 *   @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
 * ) { … }
 * ```
 *
 * [canGoBack], [isAloneInBackStack] and [isTop] are observable: reading them while composing
 * recomposes the wrapper when the back stack changes.
 */
public interface GezginWrapperScope {
  /** The route instance of this entry. */
  public val route: Route

  /**
   * The route's name as declared (for example `OptionOrderChainScreenRoute`). A constant written at
   * compile time, so R8 cannot change it.
   */
  public val routeName: String

  /**
   * The route's annotations as instances: Gezgin's own and the application's custom ones. Compiler
   * and plugin annotations (`@Serializable`, …) are not included. Empty when there are none.
   */
  public val routeAnnotations: List<Annotation>

  /** The nearest enclosing graph, `@NavGraph` or `@FlowGraph`. */
  public val graph: GezginGraph

  /** `false` for a `@NoBack` route and for an entry that is alone on the stack. */
  public val canGoBack: Boolean

  /**
   * This entry is the only one on the stack. Differs from [canGoBack]: a `@NoBack` route need not
   * be alone, and a screen opened by a deep link is.
   */
  public val isAloneInBackStack: Boolean

  /**
   * This entry is the top of the stack. A screen under an open dialog or sheet is still composed
   * and RESUMED but is not on top.
   */
  public val isTop: Boolean
}

/** A graph a route belongs to, with the graphs enclosing it reachable through [parent]. */
public class GezginGraph(
  /** The graph interface's simple name, as declared. */
  public val name: String,
  public val kind: GraphKind,
  /**
   * The graph's annotations as instances, with the same rules as
   * [GezginWrapperScope.routeAnnotations].
   */
  public val annotations: List<Annotation>,
  /** The enclosing graph (either kind), or `null` at the outermost graph. */
  public val parent: GezginGraph?,
)

/** Whether a [GezginGraph] is a `@NavGraph` or a `@FlowGraph`. */
public enum class GraphKind {
  Nav,
  Flow,
}
