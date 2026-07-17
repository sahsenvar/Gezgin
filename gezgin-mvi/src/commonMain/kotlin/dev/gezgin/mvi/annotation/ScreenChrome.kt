package dev.gezgin.mvi.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Migration-only binding for a top-bar composable rendered around one MVI [route]. This temporary
 * compatibility API preserves ZAD's existing `ColumnScope` screen shape; it is not a permanent
 * container, scrolling, or screen-scope contract.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class TopBar(val route: KClass<out Route>)

/**
 * Migration-only binding for a bottom-bar composable rendered around one MVI [route]. The generated
 * entry suppresses this provider while the IME is visible. This is not a permanent container API.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class BottomBar(val route: KClass<out Route>)
