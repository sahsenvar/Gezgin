package dev.gezgin.mvi.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel as AndroidxViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.ObserveAsEvents
import dev.gezgin.mvi.annotation.ScreenEffect
import dev.gezgin.mvi.annotation.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Task-5.0 derleme kanıtı (full codegen 5.1/5.2). Kanıtladığı üçlü + shape:
 *  - `@ViewModel(Route::class)` bir VM class'ında (CLASS target) + VM `GezginMvi<S,I,E>` implement eder
 *    (İKİSİ DE — guardrail'in doğrulayacağı biçim) → S/I/E generic şekli kullanılabilir.
 *  - stateless `@Screen(Route)` content `(state, onIntent)` + `@ScreenEffect` effects `(Flow<E>)`.
 *  - `provideXEntry` = codegen'in üreteceği ANDROIDX-FALLBACK resolver shape'i, gezgin-core entry-scoping'e
 *    (`GezginEntryScope.register<Route>`) karşı derlenir: `viewModel(factory = viewModelFactory{ initializer{} })`
 *    + `collectAsStateWithLifecycle()` + `ObserveAsEvents(...)` + stateless content çağrısı.
 * Bu bir SMOKE derleme kanıtıdır (render/back döngüsü değil — o 5.2/5.3 sample'ında).
 */

data class CounterRoute(val start: Int = 0) : Route

data class CounterState(val count: Int)
sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
}
sealed interface CounterEffect {
    data class Toast(val text: String) : CounterEffect
}

@ViewModel(CounterRoute::class)
class CounterViewModel(route: CounterRoute) :
    AndroidxViewModel(),
    GezginMvi<CounterState, CounterIntent, CounterEffect> {

    private val _uiState = MutableStateFlow(CounterState(route.start))
    override val uiState: StateFlow<CounterState> = _uiState.asStateFlow()

    // Kayıpsız backing (MJ2): gözlemci yokken (örtülen/STOPPED entry) emit edilen efekt Channel'da tutulur,
    // re-observe'de teslim edilir — MutableSharedFlow(replay=0)+tryEmit deseninin sessiz-düşürmesi YOK.
    private val _effects = GezginEffects<CounterEffect>()
    override val effects: Flow<CounterEffect> = _effects.flow

    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> _uiState.update { it.copy(count = it.count + 1) }
            CounterIntent.Decrement -> _uiState.update { it.copy(count = it.count - 1) }
        }
        _effects.send(CounterEffect.Toast("count=${_uiState.value.count}"))
    }
}

@Screen(CounterRoute::class)
@Composable
fun CounterContent(state: CounterState, onIntent: (CounterIntent) -> Unit) {
    // stateless: yalnız state okur + onIntent tetikler (UI çizimi 5.3 sample'ında).
    if (state.count < 0) onIntent(CounterIntent.Increment)
}

@ScreenEffect
@Composable
fun CounterEffects(effects: Flow<CounterEffect>) {
    ObserveAsEvents(effects) { /* CounterEffect.Toast -> show */ }
}

/**
 * Codegen'in (5.2) üreteceği ANDROIDX-FALLBACK provider'ın elle yazılmış eşdeğeri — DERLEME kanıtı.
 * `viewModel` default'u burada androidx `viewModel()` (Hilt/Koin YOK — gezgin-mvi DI-agnostik); Hilt/Koin
 * override'ları kullanıcı tarafından `provideCounterEntry(viewModel = { args -> hiltViewModel(...) })`
 * ile verilir (5.3 README).
 */
fun GezginEntryScope.provideCounterEntry(
    viewModel: @Composable (args: CounterRoute) -> CounterViewModel = { args ->
        viewModel(factory = viewModelFactory { initializer { CounterViewModel(args) } })
    },
) {
    register<CounterRoute> { route ->
        val vm = viewModel(route)
        val state by vm.uiState.collectAsStateWithLifecycle()
        CounterEffects(vm.effects)
        CounterContent(state, vm::onIntent)
    }
}
