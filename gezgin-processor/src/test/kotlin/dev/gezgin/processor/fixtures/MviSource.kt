package dev.gezgin.processor.fixtures

/**
 * Faz 5.1 positive fixture — a well-formed MVI triple (§10.1): `@ViewModel(CounterRoute::class)` VM
 * implementing `GezginMvi<S,I,E>`, a stateless MVI-mode `@Screen(CounterRoute::class)` content
 * `(state, onIntent)`, and an optional `@ScreenEffect fun CounterEffects(effects: Flow<CounterEffect>)`.
 *
 * The VM deliberately does NOT extend `androidx.lifecycle.ViewModel` — Faz 5.1 (read + validate) only
 * needs it to implement `GezginMvi`; the androidx base is a 5.2/5.3 codegen/runtime concern. Composable
 * bodies are empty (no `CompositionLocal.current` reads) so kctfork's plugin-less backend compiles
 * clean (see [dev.gezgin.processor.EntryCodegenTest]'s class KDoc on the inline-ICE caveat).
 */
val MVI_SOURCE = """
    package dev.gezgin.mviui

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ScreenEffect
    import dev.gezgin.mvi.annotation.ViewModel
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

    @ViewModel(CounterRoute::class)
    class CounterViewModel(route: CounterRoute) : GezginMvi<CounterState, CounterIntent, CounterEffect> {
        override val uiState: StateFlow<CounterState> = MutableStateFlow(CounterState(route.start))
        override val effects: Flow<CounterEffect> = emptyFlow()
        override fun onIntent(intent: CounterIntent) {}
    }

    @Screen(CounterRoute::class)
    @Composable
    fun CounterContent(state: CounterState, onIntent: (CounterIntent) -> Unit) {
    }

    @ScreenEffect
    @Composable
    fun CounterEffects(effects: Flow<CounterEffect>) {
    }
""".trimIndent()
