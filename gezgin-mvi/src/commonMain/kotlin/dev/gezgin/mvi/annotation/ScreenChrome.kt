package dev.gezgin.mvi.annotation

import dev.gezgin.core.ExperimentalGezginMigrationApi
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Migration-only binding for a top-bar composable rendered around one MVI [route]. This temporary
 * compatibility API preserves ZAD's existing `ColumnScope` screen shape; it is not a permanent
 * container, scrolling, or screen-scope contract.
 */
@ExperimentalGezginMigrationApi
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class TopBar(
    public val route: KClass<out Route>,
)

/**
 * Migration-only binding for a bottom-bar composable rendered around one MVI [route]. The generated
 * entry suppresses this provider while the IME is visible. This is not a permanent container API.
 */
@ExperimentalGezginMigrationApi
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class BottomBar(
    public val route: KClass<out Route>,
)
