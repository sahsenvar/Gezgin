package dev.gezgin.core

import dev.gezgin.core.compose.GezginTransition

/**
 * The root of every graph interface. The app's root sealed graph extends this (AppGraph : Route).
 *
 * [transition] — the runtime transition value, **getter required**: `override val transition get()
 * = transition { forward { .. } }` — the initializer form (`val transition = transition { .. }`)
 * creates a backing field on `@Serializable` data-class routes, which collides with
 * kotlinx.serialization codegen (a non-serializable field). The default `null` = "this level says
 * nothing" (the basis of the cascade): a graph interface may override this property to supply its
 * own group-wide transition; routes that implement it inherit the graph's value through Kotlin's
 * interface property-override chain unless they have their own override (the screen>graph cascade
 * comes FOR FREE — no extra code). The remaining app-level step
 * ([dev.gezgin.core.compose.navTransitions]) lives in [dev.gezgin.core.compose.resolveTransition].
 *
 * @author @sahsenvar
 */
public interface Route {
  /**
   * The route-level transition override, or `null` to inherit the graph or application transition.
   */
  public val transition: GezginTransition?
    get() = null
}
