package dev.gezgin.core.annotation

import kotlin.reflect.KClass

/**
 * Marks a composable that wraps screen content. The processor fills every parameter annotated with
 * [FilledBy] from the declarations carrying that slot marker, and calls this function in place of
 * the screen content in the generated entry.
 *
 * A wrapper must declare exactly one slot filled by a kind annotation ([Screen], [Dialog],
 * [BottomSheet] or [FullscreenModal]); that slot receives the screen body. Every other slot is
 * optional when its parameter has a Kotlin default.
 *
 * The wrapper owns everything Gezgin does not: the container, the ViewModel, state collection and
 * side-effect policy. Gezgin resolves the wrapper's type parameters from the signatures of the
 * declarations that fill its slots, and supplies the typed route and navigator to those
 * declarations as roles.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenWrapper

/**
 * Marks an application-defined annotation as a slot marker. The marked annotation must declare
 * exactly one `KClass<out Route>` parameter naming the route its providers serve; any further
 * parameters are ignored by Gezgin. Mark it `@Repeatable` to bind one provider to several routes.
 *
 * The application owns the vocabulary: a top bar, a bottom bar, an effect handler and a ViewModel
 * provider are all ordinary slot markers with names the application chooses.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenSlot

/**
 * Binds one [ScreenWrapper] parameter to the slot marker whose providers fill it.
 *
 * @property marker the [ScreenSlot]-annotated annotation whose providers fill this parameter
 * @author @sahsenvar
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class FilledBy(public val marker: KClass<out Annotation>)
