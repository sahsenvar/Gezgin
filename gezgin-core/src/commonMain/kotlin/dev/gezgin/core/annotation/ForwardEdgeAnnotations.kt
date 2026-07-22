package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import dev.gezgin.core.Self
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * Forward navigation edge (§3.1/§4.1): push to each `target` from the source graph interface.
 * Codegen emits a typed `goToX()` per target; `singleTop = true` (default) dedups an equal-valued
 * top, and `name` distinguishes multiple edges to the same target (`@Repeatable`).
 *
 * @property target route types that can be pushed through this edge
 * @property singleTop whether an equal route already at the top is reused
 * @property name an optional discriminator for multiple edges to the same target
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class GoTo(
  vararg val target: KClass<out Route>,
  val singleTop: Boolean = true,
  val name: String = "",
)

/**
 * Replace edge (§4.1): replaces the current destination with `target` — `clearUpTo` (default
 * `Self`) + `inclusive` control how much is cleared; for irreversible transitions such as
 * auth-success/onboarding. Codegen emits a typed `replaceToX()`.
 *
 * @property target the replacement route type
 * @property clearUpTo the route type that bounds stack removal
 * @property inclusive whether [clearUpTo] is also removed
 * @property name an optional discriminator for multiple edges to the same target
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class ReplaceTo(
  val target: KClass<out Route>,
  val clearUpTo: KClass<out Route> = Self::class,
  val inclusive: Boolean = true,
  val name: String = "",
)

/**
 * Result-returning forward edge (§6): `target` must be a `ResultRoute<T>` or `ResultFlow<T>`.
 * Codegen emits the PD-safe triple — `launchX()` (trigger) + `xResults` (stream) + suspend
 * `goToXForResult()` (sugar).
 *
 * @property target the result-producing destination route or flow
 * @property name an optional discriminator for multiple result edges to the same target
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class GoForResult(val target: KClass<out Route>, val name: String = "")

/**
 * Flow-exit + forward edge (§8.1): tears the current flow down with Canceled and navigates to
 * `target`. Codegen emits a typed `quitAndGoToX()`.
 *
 * @property target the route opened after the current flow exits
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class QuitAndGoTo(val target: KClass<out Route>)
