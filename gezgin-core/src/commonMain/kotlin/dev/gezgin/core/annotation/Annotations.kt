package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import dev.gezgin.core.Self
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class NavGraph

@Target(AnnotationTarget.CLASS)
annotation class FlowGraph

@Target(AnnotationTarget.CLASS)
annotation class StartDestination

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class GoTo(val target: KClass<out Route>, val singleTop: Boolean = true, val name: String = "")

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ReplaceTo(val target: KClass<out Route>, val clearUpTo: KClass<out Route> = Self::class, val inclusive: Boolean = true, val name: String = "")

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class GoForResult(val target: KClass<out Route>, val name: String = "")

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class BackTo(val target: KClass<out Route>, val inclusive: Boolean = false)

@Target(AnnotationTarget.CLASS)
annotation class BackToStart

@Target(AnnotationTarget.CLASS)
annotation class Quit

@Target(AnnotationTarget.CLASS)
annotation class QuitAndGoTo(val target: KClass<out Route>)

@Target(AnnotationTarget.CLASS)
annotation class NoBack
