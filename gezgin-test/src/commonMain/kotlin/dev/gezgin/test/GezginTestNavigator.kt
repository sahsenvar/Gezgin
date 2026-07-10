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
public class GezginTestNavigator(
    start: Route,
    topology: GezginTopology,
    onRootBack: () -> Unit = {},
) {
    public val raw: RawNavigator = RawNavigator(start = start, topology = topology, onRootBack = onRootBack)

    public val backStack: List<Route> get() = raw.backStack.value
    public val current: Route get() = raw.current

    public fun navigate(route: Route): Unit = raw.navigate(route)
    public fun back(): Unit = raw.back()
    public fun replaceTo(route: Route): Unit = raw.replaceTo(route)

    /** top pending-target'a Value teslim + pop (test yardımcı) = raw.backWithResult(result). */
    public fun deliverResult(result: Any?): Unit = raw.backWithResult(result)

    /**
     * Task 2.6 — codegen'in `fromX()` extension'larının kancası: [route]'u uygulayan EN YAKIN
     * (stack'te en üstteki) entry'nin id'si. Hiç yoksa (source hiç ziyaret edilmemiş) açıklayıcı
     * bir hata fırlatır — sessizce yanlış navigator kurmak yerine.
     */
    @OptIn(GezginInternalApi::class)
    public fun entryIdOf(route: KClass<out Route>): Long =
        raw.entryIdOf(route)
            ?: error("GezginTestNavigator.entryIdOf: no entry for ${route.simpleName} on the stack ($backStack)")
}
