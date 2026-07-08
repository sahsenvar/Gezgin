package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class BackTo(val target: KClass<out Route>, val inclusive: Boolean = false)

@Target(AnnotationTarget.CLASS)
annotation class BackToStart

@Target(AnnotationTarget.CLASS)
annotation class Quit

@Target(AnnotationTarget.CLASS)
annotation class NoBack
