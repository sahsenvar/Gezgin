package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec

private val OPT_IN = ClassName("kotlin", "OptIn")
private val GEZGIN_INTERNAL_API = ClassName("dev.gezgin.core", "GezginInternalApi")

/**
 * Emits `@file:OptIn(GezginInternalApi::class)` on a generated file (K4). Generated code is the intended
 * caller of the `@GezginInternalApi`-gated codegen hooks (entry-id `RawNavigator` overloads,
 * `GezginTopology`/`FlowType`/`EdgeSpec` constructors, the `LocalGezgin*` locals, `Route.toBundle`,
 * `bindGezgin`, `@GezginNavigatorFor`, `GezginTestNavigator.raw`), so the whole generated file opts in.
 */
internal fun FileSpec.Builder.optInGezginInternalApi(): FileSpec.Builder =
    addAnnotation(AnnotationSpec.builder(OPT_IN).addMember("%T::class", GEZGIN_INTERNAL_API).build())
