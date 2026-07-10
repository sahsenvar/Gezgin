package dev.gezgin.core.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * Tek yönlü transition spec'i — Nav3 `NavDisplay`'in `transitionSpec`/`popTransitionSpec` parametreleriyle
 * AYNI imza (§9; task-3.0-report.md "NavDisplay — gerçek imza"): `AnimatedContentTransitionScope<Scene<T>>`
 * receiver'ı üzerinde çalışır (yön/boyut bilgisine ihtiyaç duyan `slideIntoContainer` gibi extension'lar bu
 * yüzden receiver'sız bir `ContentTransform` değeri OLARAK saklanamaz — lambda saklanır, NavDisplay'e
 * verildiği anda receiver'la çağrılır). `T = Route` sabitlenmiş: Gezgin tek `Route` tipiyle çalışıyor.
 */
public typealias GezginTransitionSpec = AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform

/**
 * `predictivePopTransitionSpec` ile aynı imza — ek `Int` parametresi Nav3'te sürükleme kenarını
 * (`@NavigationEvent.SwipeEdge`) taşır; Gezgin'de opak geçirilir (adaptasyon gerekmiyor).
 */
public typealias GezginPredictiveTransitionSpec = AnimatedContentTransitionScope<Scene<Route>>.(Int) -> ContentTransform

/**
 * Bir route'un (veya app/graph seviyesinin) runtime transition değeri (§9). Üç alan da opsiyonel:
 * `null` = "bu seviye bir şey söylemiyor" — cascade bir üst seviyeye (graph > app > NavDisplay
 * default'u) düşer ([resolveTransition]). `predictive` yazılmazsa NavDisplay wiring'i (`GezginDisplay`)
 * onu `backward` ile doldurur (§9 "predictive yazılmazsa = backward").
 */
public class GezginTransition(
    public val forward: GezginTransitionSpec? = null,
    public val backward: GezginTransitionSpec? = null,
    public val predictive: GezginPredictiveTransitionSpec? = null,
)

/** [transition] builder'ı — `forward { }` / `backward { }` / `predictive { }` çağrıları [GezginTransition] alanlarını doldurur. */
public class GezginTransitionBuilder {
    private var forward: GezginTransitionSpec? = null
    private var backward: GezginTransitionSpec? = null
    private var predictive: GezginPredictiveTransitionSpec? = null

    public fun forward(spec: GezginTransitionSpec) {
        forward = spec
    }

    public fun backward(spec: GezginTransitionSpec) {
        backward = spec
    }

    public fun predictive(spec: GezginPredictiveTransitionSpec) {
        predictive = spec
    }

    internal fun build(): GezginTransition = GezginTransition(forward, backward, predictive)
}

/**
 * `override val transition get() = transition { forward { .. }; backward { .. } }` (§3.1/§9) — **her zaman
 * bir getter içinde çağrılmalı**, backing field'a atanmamalı: initializer'lı hâli (`val transition =
 * transition { .. }`) route'un `@Serializable` data class'ının kotlinx.serialization codegen'iyle
 * çakışır (non-serializable bir alan constructor'a/equals'a sızar) — bu yüzden §9 "getter zorunlu" diyor.
 */
public fun transition(block: GezginTransitionBuilder.() -> Unit): GezginTransition =
    GezginTransitionBuilder().apply(block).build()

/**
 * App-seviyesi (default) transition — `GezginDisplay(transitions = navTransitions { forward { } backward { } })`
 * (§12). Route/graph zincirinin HİÇBİRİ bir şey söylemediğinde ([Route.transition] `null`) kullanılacak
 * son çare. Route-seviyesi [transition] ile AYNI şekle ([GezginTransition]) sahip — ayrı bir sarmalayıcı
 * tip yok (V1'de app-seviyesi tek bir default'tan ibaret).
 */
public fun navTransitions(block: GezginTransitionBuilder.() -> Unit): GezginTransition = transition(block)

/**
 * Cascade çözümü (§9: "en içteki (screen) > graph > app"). [Route.transition] screen>graph zincirini
 * zaten TAŞIR (Kotlin interface property override zinciri); burada eklenen tek basamak: route zinciri
 * `null` dönerse app-seviyesi [appTransition]'a düş. Sonuç yine `null`sa çağıran ([GezginDisplay])
 * NavDisplay'in kendi default'larını kullanır.
 */
internal fun resolveTransition(route: Route, appTransition: GezginTransition?): GezginTransition? =
    route.transition ?: appTransition

/**
 * Çözülmüş cascade değerini Nav3 per-entry metadata'sına indirir (Task 3.5 fix — §9 "route (NavKey) →
 * entry metadata'sındaki `NavDisplay.TransitionKey` ailesine iner"): `NavDisplay.transitionSpec/
 * popTransitionSpec/predictivePopTransitionSpec` PUBLIC sarmalayıcıları HER İKİ target'ta da (desktop
 * alpha05 VE android 1.1.4 — decompile ile doğrulandı, aynı `Map<String, Any>` dönen imza aynı commonMain
 * dosyasında) mevcut; map anahtarı platform-içi tutarlı olduğu sürece (sarmalayıcı üretiyor) değer
 * NavDisplay'in AnimatedContent çözümünde `Scene.metadata` (default'u = SON entry'nin metadata'sı,
 * `Scene.kt`) üzerinden NavDisplay-seviyesi parametrelerden ÖNCE okunur.
 *
 * `null` alanların anahtarı HİÇ eklenmez → Nav3'ün kendi fallback zinciri (entry metadata → NavDisplay
 * default'ları) çalışır. Predictive: `predictive ?: backward` (§9 "predictive yazılmazsa = backward") — backward de
 * `null`sa predictive anahtarı da eklenmez (ikisi birden NavDisplay default'una düşer).
 *
 * Cast notu: sarmalayıcılar `AnimatedContentTransitionScope<Scene<*>>` receiver'ı bekler, Gezgin spec'leri
 * `Scene<Route>`'a sabitli — Gezgin'in `NavDisplay<Route>` kurulumunda runtime tipi hep `Scene<Route>`
 * olduğundan unchecked-cast güvenli.
 */
@Suppress("UNCHECKED_CAST")
internal fun GezginTransition.toNavEntryMetadata(): Map<String, Any> {
    var metadata = emptyMap<String, Any>()
    forward?.let { spec ->
        metadata = metadata + NavDisplay.transitionSpec {
            spec.invoke(this as AnimatedContentTransitionScope<Scene<Route>>)
        }
    }
    backward?.let { spec ->
        metadata = metadata + NavDisplay.popTransitionSpec {
            spec.invoke(this as AnimatedContentTransitionScope<Scene<Route>>)
        }
    }
    val effectivePredictive: GezginPredictiveTransitionSpec? =
        predictive ?: backward?.let { b -> { _: Int -> b() } }   // §9: predictive yazılmazsa = backward
    effectivePredictive?.let { spec ->
        metadata = metadata + NavDisplay.predictivePopTransitionSpec { edge ->
            spec.invoke(this as AnimatedContentTransitionScope<Scene<Route>>, edge)
        }
    }
    return metadata
}
