package dev.gezgin.test

import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * §13 — UI-less test API, raw surface (Faz 2 codegen brings typed `from<Source>()`).
 * Thin delegation over [RawNavigator] for tests that don't want to stand up a display layer.
 */
class GezginTestNavigator(
    start: Route,
    topology: GezginTopology,
    onRootBack: () -> Unit = {},
) {
    val raw: RawNavigator = RawNavigator(start = start, topology = topology, onRootBack = onRootBack)

    val backStack: List<Route> get() = raw.backStack.value
    val current: Route get() = raw.current

    fun navigate(route: Route) = raw.navigate(route)
    fun back() = raw.back()
    fun replaceTo(route: Route) = raw.replaceTo(route)

    /** top pending-target'a Value teslim + pop (test yardımcı) = raw.backWithResult(result). */
    fun deliverResult(result: Any?) = raw.backWithResult(result)

    /**
     * Task 2.6 — codegen'in `fromX()` extension'larının kancası: [route]'u uygulayan EN YAKIN
     * (stack'te en üstteki) entry'nin id'si. Hiç yoksa (source hiç ziyaret edilmemiş) açıklayıcı
     * bir hata fırlatır — sessizce yanlış navigator kurmak yerine.
     */
    @OptIn(GezginInternalApi::class)
    fun entryIdOf(route: KClass<out Route>): Long =
        raw.entryIdOf(route)
            ?: error("GezginTestNavigator.entryIdOf: no entry for ${route.simpleName} on the stack ($backStack)")
}
