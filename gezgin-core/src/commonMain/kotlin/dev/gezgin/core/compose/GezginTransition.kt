package dev.gezgin.core.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * A one-directional transition spec — the SAME signature as Nav3 `NavDisplay`'s `transitionSpec`/
 * `popTransitionSpec` parameters: it runs on an `AnimatedContentTransitionScope<Scene<T>>` receiver
 * (extensions like `slideIntoContainer` that need direction/size info therefore cannot be stored AS
 * a receiver-less `ContentTransform` value — the lambda is stored and invoked with the receiver the
 * moment it is handed to NavDisplay). `T = Route` is fixed: Gezgin works with the single `Route`
 * type.
 */
public typealias GezginTransitionSpec =
  AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform

/**
 * The same signature as `predictivePopTransitionSpec` — the extra `Int` parameter carries the drag
 * edge in Nav3 (`@NavigationEvent.SwipeEdge`); Gezgin passes it through opaquely (no adaptation
 * needed).
 */
public typealias GezginPredictiveTransitionSpec =
  AnimatedContentTransitionScope<Scene<Route>>.(Int) -> ContentTransform

/**
 * The runtime transition value of a route (or of the app/graph level). All fields are optional:
 * `null` = "this level says nothing" — the cascade falls to a higher level (graph > app >
 * NavDisplay default) ([resolveTransition]). If `predictive` is not written, the NavDisplay wiring
 * (`GezginDisplay`) fills it from `backward` ("if predictive is not written = backward").
 *
 * [switch] is the tab-switch transition, used instead of [forward] when a `@Switch` edge brings
 * another tab forward. Unlike the other fields it cascades per field (route > app > a built-in
 * cross-fade), so an app that only customizes [forward] keeps the cross-fade for tab switches.
 *
 * @author @sahsenvar
 */
public class GezginTransition(
  /** The transition used when navigating forward, or `null` to inherit a broader default. */
  public val forward: GezginTransitionSpec? = null,
  /** The transition used when popping, or `null` to inherit a broader default. */
  public val backward: GezginTransitionSpec? = null,
  /** The predictive-back transition, or `null` to fall back to [backward]. */
  public val predictive: GezginPredictiveTransitionSpec? = null,
  /** The transition used when switching tabs, or `null` to inherit; the default is a cross-fade. */
  public val switch: GezginTransitionSpec? = null,
) {
  /** Source- and binary-compatible constructor without a tab-switch transition. */
  public constructor(
    forward: GezginTransitionSpec?,
    backward: GezginTransitionSpec?,
    predictive: GezginPredictiveTransitionSpec?,
  ) : this(forward, backward, predictive, null)
}

/**
 * Builds a [GezginTransition] from directional transition specifications.
 *
 * @author @sahsenvar
 */
public class GezginTransitionBuilder {
  private var forward: GezginTransitionSpec? = null
  private var backward: GezginTransitionSpec? = null
  private var predictive: GezginPredictiveTransitionSpec? = null
  private var switch: GezginTransitionSpec? = null

  /** Sets the forward-navigation [spec]. */
  public fun forward(spec: GezginTransitionSpec) {
    forward = spec
  }

  /** Sets the pop-navigation [spec]. */
  public fun backward(spec: GezginTransitionSpec) {
    backward = spec
  }

  /** Sets the predictive-back [spec]. */
  public fun predictive(spec: GezginPredictiveTransitionSpec) {
    predictive = spec
  }

  /** Sets the tab-switch [spec], used when a `@Switch` edge brings another tab forward. */
  public fun switch(spec: GezginTransitionSpec) {
    switch = spec
  }

  internal fun build(): GezginTransition = GezginTransition(forward, backward, predictive, switch)
}

/**
 * `override val transition get() = transition { forward { .. }; backward { .. } }` — **always call
 * it inside a getter**, never assign it to a backing field: the initializer form (`val transition =
 * transition { .. }`) collides with the kotlinx.serialization codegen of the route's
 * `@Serializable` data class (a non-serializable field leaks into the constructor/equals) — which
 * is why says "getter required".
 */
public fun transition(block: GezginTransitionBuilder.() -> Unit): GezginTransition =
  GezginTransitionBuilder().apply(block).build()

/**
 * App-level (default) transition — `GezginDisplay(transitions = navTransitions { forward { }
 * backward { } })`. The last resort used when NOTHING in the route/graph chain says anything
 * ([Route.transition] `null`). It has the SAME shape ([GezginTransition]) as a route-level
 * [transition] — there is no separate wrapper type (in V1 the app level is just a single default).
 */
public fun navTransitions(block: GezginTransitionBuilder.() -> Unit): GezginTransition =
  transition(block)

/**
 * Cascade resolution: "innermost (screen) > graph > app". [Route.transition] already CARRIES the
 * screen>graph chain (Kotlin's interface property-override chain); the only step added here: if the
 * route chain returns `null`, fall to the app-level [appTransition]. If the result is still `null`,
 * the caller ([GezginDisplay]) uses NavDisplay's own defaults.
 */
internal fun resolveTransition(route: Route, appTransition: GezginTransition?): GezginTransition? =
  route.transition ?: appTransition

/** Cross-fade used for tab switches when neither the route nor the app level supplies one. */
private val DefaultTabSwitchTransition: GezginTransitionSpec = {
  fadeIn(tween(TAB_SWITCH_FADE_MILLIS)) togetherWith fadeOut(tween(TAB_SWITCH_FADE_MILLIS))
}

private const val TAB_SWITCH_FADE_MILLIS = 150

/** Per-field cascade for the tab-switch transition: route > app > built-in cross-fade. */
internal fun resolveTabSwitchTransition(
  route: Route,
  appTransition: GezginTransition?,
): GezginTransitionSpec =
  route.transition?.switch ?: appTransition?.switch ?: DefaultTabSwitchTransition

/** Entry-metadata key carrying the tab segment id of an entry (a `Long`). */
internal const val GEZGIN_TAB_SEGMENT_KEY: String = "dev.gezgin.core.tabSegment"

private fun Scene<*>.tabSegment(): Long? =
  entries.lastOrNull()?.metadata?.get(GEZGIN_TAB_SEGMENT_KEY) as? Long

/**
 * Lowers the resolved cascade value into Nav3 per-entry metadata: the route becomes an entry whose
 * metadata uses the `NavDisplay.TransitionKey` family. The PUBLIC wrappers
 * `NavDisplay.transitionSpec/popTransitionSpec/predictivePopTransitionSpec` exist on BOTH targets
 * (desktop alpha05 AND android 1.1.4 — verified via decompile, the same `Map<String,
 * Any>`-returning signature in the same commonMain file); as long as the map key is consistent
 * within a platform (the wrapper produces it), the value is read in NavDisplay's AnimatedContent
 * resolution via `Scene.metadata` (whose default = the LAST entry's metadata, `Scene.kt`), BEFORE
 * the NavDisplay-level parameters.
 *
 * The key of a `null` field is never added → Nav3's own fallback chain (entry metadata → NavDisplay
 * defaults) runs. Predictive: `predictive ?: backward` ("if predictive is not written = backward")
 * — if backward is also `null`, the predictive key is not added either (both fall to the NavDisplay
 * default).
 *
 * Cast note: the wrappers expect an `AnimatedContentTransitionScope<Scene<*>>` receiver, while
 * Gezgin's specs are fixed to `Scene<Route>` — since the runtime type is always `Scene<Route>` in
 * Gezgin's `NavDisplay<Route>` setup, the unchecked cast is safe.
 */
@Suppress("UNCHECKED_CAST")
internal fun GezginTransition?.toNavEntryMetadata(
  tabSwitch: GezginTransitionSpec? = null
): Map<String, Any> {
  var metadata = emptyMap<String, Any>()
  val forward = this?.forward
  if (tabSwitch != null) {
    // Tab-aware forward: the transition between two different tab segments is the tab-switch
    // transition; within one segment the route's own forward (or Nav3's default, via null) runs.
    metadata =
      metadata +
        NavDisplay.transitionSpec {
          val scope = this as AnimatedContentTransitionScope<Scene<Route>>
          val from = initialState.tabSegment()
          val to = targetState.tabSegment()
          if (from != null && to != null && from != to) tabSwitch.invoke(scope)
          else forward?.invoke(scope)
        }
  } else if (forward != null) {
    metadata =
      metadata +
        NavDisplay.transitionSpec {
          forward.invoke(this as AnimatedContentTransitionScope<Scene<Route>>)
        }
  }
  val backward = this?.backward
  backward?.let { spec ->
    metadata =
      metadata +
        NavDisplay.popTransitionSpec {
          spec.invoke(this as AnimatedContentTransitionScope<Scene<Route>>)
        }
  }
  val effectivePredictive: GezginPredictiveTransitionSpec? =
    this?.predictive
      ?: backward?.let { b -> { _: Int -> b() } } // Fall back to the ordinary back transition.
  effectivePredictive?.let { spec ->
    metadata =
      metadata +
        NavDisplay.predictivePopTransitionSpec { edge ->
          spec.invoke(this as AnimatedContentTransitionScope<Scene<Route>>, edge)
        }
  }
  return metadata
}
