package dev.gezgin.mvi.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Binds an MVI effect composable to one concrete [route]. The function takes `Flow<E>` and may take
 * that route's typed navigator in a `nav` parameter. Repeating the annotation is useful when routes
 * share both the handler function and effect type.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class EffectHandler(val route: KClass<out Route>)
