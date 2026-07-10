package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import dev.gezgin.core.Self
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * Forward navigation edge (§3.1/§4.1): push to `target` from the source graph interface. Codegen emits a
 * typed `goToX()`; `singleTop = true` (default) dedups an equal-valued top, and `name` distinguishes
 * multiple edges to the same target (`@Repeatable`).
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class GoTo(val target: KClass<out Route>, val singleTop: Boolean = true, val name: String = "")

/**
 * Replace edge (§4.1): replaces the current destination with `target` — `clearUpTo` (default `Self`) +
 * `inclusive` control how much is cleared; for irreversible transitions such as auth-success/onboarding.
 * Codegen emits a typed `replaceToX()`.
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class ReplaceTo(val target: KClass<out Route>, val clearUpTo: KClass<out Route> = Self::class, val inclusive: Boolean = true, val name: String = "")

/**
 * Result-returning forward edge (§6): `target` must be a `ResultRoute<T>` or `ResultFlow<T>`. Codegen emits
 * the PD-safe triple — `launchX()` (trigger) + `xResults` (stream) + suspend `goToXForResult()` (sugar).
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class GoForResult(val target: KClass<out Route>, val name: String = "")

/**
 * Flow-exit + forward edge (§8.1): tears the current flow down with Canceled and navigates to `target`.
 * Codegen emits a typed `quitAndGoToX()`.
 */
@Target(AnnotationTarget.CLASS)
public annotation class QuitAndGoTo(val target: KClass<out Route>)
