package dev.gezgin.processor.fixtures

/**
 * Faz 5.1 positive fixture — a well-formed MVI triple (§10.1): `@MviViewModel(CounterRoute::class)`
 * VM implementing `GezginMvi<S,I,E>`, a stateless MVI-mode `@Screen(CounterRoute::class)` content
 * `(state, onIntent)`, and a route-explicit `@EffectHandler(CounterRoute::class)`.
 *
 * The VM deliberately does NOT extend `androidx.lifecycle.ViewModel` — Faz 5.1 (read + validate)
 * only needs it to implement `GezginMvi`; the androidx base is a 5.2/5.3 codegen/runtime concern.
 * Composable bodies are empty (no `CompositionLocal.current` reads) so kctfork's plugin-less
 * backend compiles clean (see [dev.gezgin.processor.EntryCodegenTest]'s class KDoc on the
 * inline-ICE caveat).
 */
val MVI_SOURCE =
  """
      package dev.gezgin.mviui

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.mvi.GezginMvi
      import dev.gezgin.mvi.annotation.EffectHandler
      import dev.gezgin.mvi.annotation.MviViewModel
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.flow.MutableStateFlow
      import kotlinx.coroutines.flow.StateFlow
      import kotlinx.coroutines.flow.emptyFlow

      data class CounterRoute(val start: Int = 0) : Route

      data class CounterState(val count: Int)
      sealed interface CounterIntent {
          data object Increment : CounterIntent
      }
      sealed interface CounterEffect {
          data class Toast(val text: String) : CounterEffect
      }

      @MviViewModel(CounterRoute::class)
      class CounterViewModel(route: CounterRoute) : GezginMvi<CounterState, CounterIntent, CounterEffect> {
          override val uiState: StateFlow<CounterState> = MutableStateFlow(CounterState(route.start))
          override val effects: Flow<CounterEffect> = emptyFlow()
          override fun onIntent(intent: CounterIntent) {}
      }

      @Screen(CounterRoute::class)
      @Composable
      fun CounterContent(state: CounterState, onIntent: (CounterIntent) -> Unit) {
      }

      @EffectHandler(CounterRoute::class)
      @Composable
      fun CounterEffects(effects: Flow<CounterEffect>) {
      }
  """
    .trimIndent()

/**
 * Task 3 route-explicit fixture: one composable binds two routes with route-local VM/effect/nav
 * types.
 */
val ROUTE_EXPLICIT_MVI_SOURCE =
  """
      package dev.gezgin.routeexplicit

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.mvi.GezginMvi
      import dev.gezgin.mvi.annotation.EffectHandler
      import dev.gezgin.mvi.annotation.MviViewModel
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.flow.MutableStateFlow
      import kotlinx.coroutines.flow.StateFlow

      @NavGraph
      sealed interface G : Route {
          @GoTo(B::class)
          data object A : G

          @GoTo(A::class)
          data object B : G
      }

      data class SharedState(val value: String)
      sealed interface SharedIntent { data object Submit : SharedIntent }
      data class EffectA(val value: String)
      data class EffectB(val value: Int)

      @MviViewModel(G.A::class)
      class VmA : GezginMvi<SharedState, SharedIntent, EffectA> {
          override val uiState: StateFlow<SharedState> = MutableStateFlow(SharedState("a"))
          override fun onIntent(intent: SharedIntent) {}
      }

      @MviViewModel(G.B::class)
      class VmB : GezginMvi<SharedState, SharedIntent, EffectB> {
          override val uiState: StateFlow<SharedState> = MutableStateFlow(SharedState("b"))
          override fun onIntent(intent: SharedIntent) {}
      }

      @Screen(G.A::class)
      @Screen(G.B::class)
      @Composable
      fun SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit) {}

      @EffectHandler(G.A::class)
      @Composable
      fun EffectsA(effects: Flow<EffectA>, nav: ANavigator) {}

      @EffectHandler(G.B::class)
      @Composable
      fun EffectsB(nav: BNavigator, effects: Flow<EffectB>) {}
  """
    .trimIndent()

/** Task 4 fixture: repeated MVI screen with route-local effects and migration-only chrome. */
val ROUTE_CHROME_MVI_SOURCE =
  """
      @file:OptIn(dev.gezgin.core.ExperimentalGezginMigrationApi::class)

      package dev.gezgin.routechrome

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.mvi.GezginMvi
      import dev.gezgin.mvi.annotation.BottomBar
      import dev.gezgin.mvi.annotation.EffectHandler
      import dev.gezgin.mvi.annotation.MviViewModel
      import dev.gezgin.mvi.annotation.TopBar
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.flow.MutableStateFlow
      import kotlinx.coroutines.flow.StateFlow

      @NavGraph
      sealed interface G : Route {
          @GoTo(B::class)
          data object A : G

          @GoTo(A::class)
          data object B : G
      }

      data class SharedState(val value: String)
      sealed interface SharedIntent { data object Submit : SharedIntent }
      data class EffectA(val value: String)
      data class EffectB(val value: Int)

      @MviViewModel(G.A::class)
      class VmA : GezginMvi<SharedState, SharedIntent, EffectA> {
          override val uiState: StateFlow<SharedState> = MutableStateFlow(SharedState("a"))
          override fun onIntent(intent: SharedIntent) {}
      }

      @MviViewModel(G.B::class)
      class VmB : GezginMvi<SharedState, SharedIntent, EffectB> {
          override val uiState: StateFlow<SharedState> = MutableStateFlow(SharedState("b"))
          override fun onIntent(intent: SharedIntent) {}
      }

      @Screen(G.A::class)
      @Screen(G.B::class)
      @Composable
      fun SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit) {}

      @EffectHandler(G.A::class)
      @Composable
      fun EffectsA(effects: Flow<EffectA>, nav: ANavigator) {}

      @EffectHandler(G.B::class)
      @Composable
      fun EffectsB(nav: BNavigator, effects: Flow<EffectB>) {}

      @TopBar(G.A::class)
      @Composable
      fun RouteATopBar(state: SharedState, onIntent: (SharedIntent) -> Unit) {}

      @BottomBar(G.A::class)
      @Composable
      fun RouteABottomBar(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
  """
    .trimIndent()
