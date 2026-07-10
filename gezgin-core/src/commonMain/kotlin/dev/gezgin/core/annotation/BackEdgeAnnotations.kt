package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * Back edge (§4.2): pops the stack up to `target` (also pops `target` when `inclusive = true`). Codegen
 * emits a typed `backToX()`; if `target` is not on the stack, `NavEvent.BackToTargetMissing` is emitted and
 * nothing is popped.
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class BackTo(val target: KClass<out Route>, val inclusive: Boolean = false)

/** Back edge (§4.2): pops the stack down to the start destination. Codegen emits a typed `backToStart()`. */
@Target(AnnotationTarget.CLASS)
public annotation class BackToStart

/** Flow-exit edge (§8.1): tears the current flow down with Canceled (at root → `onRootBack`). Codegen emits a typed `quit()`. */
@Target(AnnotationTarget.CLASS)
public annotation class Quit

/**
 * Terminal marker (§4.2): while this route is on top, back (including system-back/predictive) is SWALLOWED —
 * a Gezgin-owned handler wins the dispatcher's LIFO. The root is exempt (back at the bottom = `onRootBack`;
 * the user is never trapped in the app).
 */
@Target(AnnotationTarget.CLASS)
public annotation class NoBack
