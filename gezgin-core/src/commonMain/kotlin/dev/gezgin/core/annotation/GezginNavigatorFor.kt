package dev.gezgin.core.annotation

import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * INTERNAL CODEGEN CONTRACT (do not use by hand). `NavigatorCodegen` stamps every generated
 * `XNavigator` class with its origin route's [KClass] — carrying the route IDENTITY, not just the
 * class NAME.
 *
 * Why: the cross-module nav-wiring PROBE (`NavigatorProbe`) looks a route's navigator up on the
 * classpath via `getClassDeclarationByName("${routePkg}.${x}Navigator")`. The `x` derivation
 * (`stripSuffix`) can clash — `HelpRoute` and `HelpScreenRoute` both derive `x=Help` → both match
 * the NAME `HelpNavigator`. A name-only probe would silently bind a foreign route's navigator to a
 * display-only route (it compiles, the cast holds, and navigation follows the wrong edges). Once
 * the probe finds a class it compares [route] against the entry's routeFq → nav is bound only on an
 * IDENTITY match.
 *
 * Gated behind [GezginInternalApi] (K4): only generated code applies it; it stays a BINARY-retained
 * classpath stamp for the cross-module KSP read.
 *
 * @property route the route whose generated navigator is annotated
 */
@GezginInternalApi
@Target(AnnotationTarget.CLASS)
public annotation class GezginNavigatorFor(val route: KClass<out Route>)
