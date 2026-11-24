package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * Marks a route as the root of a tab. Every `@Tab` route owns its own back stack that stays alive
 * while another tab is active: scroll position, filters and loaded data survive a tab switch
 * because the tab's entries — and their ViewModels — are never recreated.
 *
 * Tabs are brought forward with [Switch]. A switch is not a back-stack step: back never walks
 * between tabs, and back at a tab root leaves the app. A tab root must be a screen (not a modal),
 * must not belong to a `@FlowGraph`, must not be a result or callback route, and must be
 * constructible without arguments so the generated `switchToX()` can create it on first use.
 *
 * Make the tab root the root of the stack (the start route, or a `@ReplaceTo` that clears the
 * stack) — a tab root that was merely pushed on top of another screen cannot switch tabs.
 *
 * Name-clash note: shares the simple name `Tab` with Material's `Tab` composable; an import alias
 * resolves it when both are used in one file.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class Tab

/**
 * Tab-switch edge: brings the tab rooted at each `target` forward, from a `@Tab` source route.
 * Codegen emits a typed `switchToX()` per target. Both the source and every target must be [Tab]
 * routes (`TB1`/`TB2`). The tab being left keeps its stack; a tab that was never opened is created
 * as `[target]`. Switching to the active tab does nothing.
 *
 * `@ReplaceTo`/`clearUpTo` that cuts down to a tab root drops every tab.
 *
 * @property target tab roots that can be switched to through this edge
 * @property name an optional method name replacing the derived `switchToX`
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class Switch(vararg val target: KClass<out Route>, val name: String = "")
