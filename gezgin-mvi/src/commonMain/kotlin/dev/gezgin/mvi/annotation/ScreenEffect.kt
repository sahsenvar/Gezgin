package dev.gezgin.mvi.annotation

/**
 * Legacy optional effect binder. It stands on a composable function with the signature
 * `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` and does its own [dev.gezgin.mvi.ObserveEffects].
 *
 * The processor accepts this no-argument bridge only when its exact `Flow<E>` type identifies one
 * unoccupied route. New code must use route-explicit [EffectHandler].
 */
@Target(AnnotationTarget.FUNCTION)
@Deprecated(
    message = "Route inference is ambiguous for shared screens. Use @EffectHandler(Route::class).",
    replaceWith = ReplaceWith("EffectHandler(Route::class)"),
)
public annotation class ScreenEffect
