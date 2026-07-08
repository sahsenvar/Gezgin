package dev.gezgin.core.compose

import dev.gezgin.core.Route

/**
 * App-seviyesi transition tablosu (§12: `GezginDisplay(transitions = navTransitions { default { } })`).
 * V1'de tek alan var: [default] — route/graph zincirinin HİÇBİRİ bir şey söylemediğinde ([Route.transition]
 * `null`) kullanılacak son çare. Bu da `null` olabilir (hiç app-seviyesi transition tanımlanmamış) — o
 * durumda [resolveTransition] `null` döner ve `GezginDisplay` NavDisplay'in kendi (`defaultTransitionSpec`
 * ailesi) default'larına düşer.
 */
class GezginTransitions(val default: GezginTransition?)

/** [navTransitions] builder'ı. */
class GezginTransitionsBuilder {
    private var default: GezginTransition? = null

    /** `default { forward { .. }; back { .. } }` — app-geneli transition (§12). */
    fun default(block: GezginTransitionBuilder.() -> Unit) {
        default = transition(block)
    }

    internal fun build(): GezginTransitions = GezginTransitions(default)
}

fun navTransitions(block: GezginTransitionsBuilder.() -> Unit): GezginTransitions =
    GezginTransitionsBuilder().apply(block).build()

/**
 * Cascade çözümü (§9: "Cascade en içteki (screen) > graph > app"). [Route.transition] zaten
 * screen>graph zincirini TAŞIR: bir route kendi `transition`'ını override etmemişse, Kotlin'in interface
 * property override zinciri sayesinde en yakın ata graph interface'inin override'ı devreye girer (o da
 * yoksa kök [Route] varsayılanı olan `null`). Burada eklenen tek basamak: route zinciri `null` dönerse
 * (hiçbir seviye bir şey söylemedi) app-seviyesi [GezginTransitions.default]'a düş. Sonuç yine `null`sa
 * çağıran ([dev.gezgin.core.compose.GezginDisplay]) NavDisplay'in kendi default'larını kullanır.
 */
internal fun resolveTransition(route: Route, transitions: GezginTransitions): GezginTransition? =
    route.transition ?: transitions.default
