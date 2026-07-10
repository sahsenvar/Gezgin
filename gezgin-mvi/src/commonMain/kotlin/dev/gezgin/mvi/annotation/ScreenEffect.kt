package dev.gezgin.mvi.annotation

/**
 * The optional effect binder (§10.1) — it stands on a **composable function** like `@Screen` (FUNCTION
 * target). Signature: `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` — it does its own
 * [dev.gezgin.mvi.ObserveEffects] (Google's state-first recommendation → effects are not imposed).
 *
 * Codegen (5.2) matches it to the same route as `@MviViewModel`/`@Screen` (the same-module rule) and feeds
 * it the VM's `effects: Flow<E>` stream inside `provideXEntry`; the E of `Flow<E>` is validated against the E
 * in the VM's `GezginMvi<S,I,E>` supertype. The route binding comes from the matching `@Screen`/`@MviViewModel`
 * — the annotation itself takes no parameters.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class ScreenEffect
