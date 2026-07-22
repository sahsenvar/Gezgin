package dev.gezgin.core.annotation

/**
 * Transparent graph container (§3/§8.1): marks a sealed interface that groups routes; its members
 * are ordinary stack entries (NO flow-unit boundary). Every route belongs to exactly one graph.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class NavGraph

/**
 * Opaque flow container (§8.1): marks a sealed interface whose members form a single flow-unit;
 * `quit`/`quitWith` tears the WHOLE unit down. Combined with `ResultFlow<T>` it carries result
 * ownership.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class FlowGraph

/**
 * Marks the start destination inside a `@NavGraph`/`@FlowGraph` (§8.1) — the first route opened on
 * entering the container.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class StartDestination
