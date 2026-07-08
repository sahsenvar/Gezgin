package dev.gezgin.core.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene
import dev.gezgin.core.Route

/**
 * Tek yönlü transition spec'i — Nav3 `NavDisplay`'in `transitionSpec`/`popTransitionSpec` parametreleriyle
 * AYNI imza (§9; task-3.0-report.md "NavDisplay — gerçek imza"): `AnimatedContentTransitionScope<Scene<T>>`
 * receiver'ı üzerinde çalışır (yön/boyut bilgisine ihtiyaç duyan `slideIntoContainer` gibi extension'lar bu
 * yüzden receiver'sız bir `ContentTransform` değeri OLARAK saklanamaz — lambda saklanır, NavDisplay'e
 * verildiği anda receiver'la çağrılır). `T = Route` sabitlenmiş: Gezgin tek `Route` tipiyle çalışıyor.
 */
typealias GezginTransitionSpec = AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform

/**
 * `predictivePopTransitionSpec` ile aynı imza — ek `Int` parametresi Nav3'te sürükleme kenarını
 * (`@NavigationEvent.SwipeEdge`) taşır; Gezgin'de opak geçirilir (adaptasyon gerekmiyor).
 */
typealias GezginPredictiveTransitionSpec = AnimatedContentTransitionScope<Scene<Route>>.(Int) -> ContentTransform

/**
 * Bir route'un (veya app/graph seviyesinin) runtime transition değeri (§9). Üç alan da opsiyonel:
 * `null` = "bu seviye bir şey söylemiyor" — cascade bir üst seviyeye (graph > app > NavDisplay
 * default'u) düşer ([resolveTransition]). `predictive` yazılmazsa NavDisplay wiring'i (`GezginDisplay`)
 * onu `back` ile doldurur (§9 "predictive yazılmazsa = back").
 */
class GezginTransition(
    val forward: GezginTransitionSpec? = null,
    val back: GezginTransitionSpec? = null,
    val predictive: GezginPredictiveTransitionSpec? = null,
)

/** [transition] builder'ı — `forward { }` / `back { }` / `predictive { }` çağrıları [GezginTransition] alanlarını doldurur. */
class GezginTransitionBuilder {
    private var forward: GezginTransitionSpec? = null
    private var back: GezginTransitionSpec? = null
    private var predictive: GezginPredictiveTransitionSpec? = null

    fun forward(spec: GezginTransitionSpec) {
        forward = spec
    }

    fun back(spec: GezginTransitionSpec) {
        back = spec
    }

    fun predictive(spec: GezginPredictiveTransitionSpec) {
        predictive = spec
    }

    internal fun build(): GezginTransition = GezginTransition(forward, back, predictive)
}

/**
 * `override val transition get() = transition { forward { .. }; back { .. } }` (§3.1/§9) — **her zaman
 * bir getter içinde çağrılmalı**, backing field'a atanmamalı: initializer'lı hâli (`val transition =
 * transition { .. }`) route'un `@Serializable` data class'ının kotlinx.serialization codegen'iyle
 * çakışır (non-serializable bir alan constructor'a/equals'a sızar) — bu yüzden §9 "getter zorunlu" diyor.
 */
fun transition(block: GezginTransitionBuilder.() -> Unit): GezginTransition =
    GezginTransitionBuilder().apply(block).build()
