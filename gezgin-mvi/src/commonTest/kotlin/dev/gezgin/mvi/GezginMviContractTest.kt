package dev.gezgin.mvi

import dev.gezgin.mvi.fixtures.CounterEffect
import dev.gezgin.mvi.fixtures.CounterIntent
import dev.gezgin.mvi.fixtures.CounterRoute
import dev.gezgin.mvi.fixtures.CounterViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task-5.0: `@ViewModel` + `GezginMvi<S,I,E>` sözleşmesinin birlikte derlendiğini VE S/I/E generic
 * şeklinin çalışır olduğunu kanıtlayan minimal runnable test (compose'suz, saf Kotlin — §13 ruhu).
 * Codegen okuması (S/I/E extraction, guardrail) 5.1'de; bu yalnız runtime sözleşmesini doğrular.
 */
class GezginMviContractTest {

    @Test
    fun uiState_startsFromRoute_and_onIntent_updates() {
        val vm = CounterViewModel(CounterRoute(start = 5))
        val mvi: GezginMvi<*, CounterIntent, *> = vm

        assertEquals(5, vm.uiState.value.count)
        mvi.onIntent(CounterIntent.Increment)
        assertEquals(6, vm.uiState.value.count)
        mvi.onIntent(CounterIntent.Decrement)
        mvi.onIntent(CounterIntent.Decrement)
        assertEquals(4, vm.uiState.value.count)
    }

    @Test
    fun effects_flow_emits_on_intent() = runTest {
        val vm = CounterViewModel(CounterRoute())
        val received = mutableListOf<CounterEffect>()
        // effects = replay=0 SharedFlow → collector'ı emit'ten ÖNCE bağlamalı (UnconfinedTestDispatcher
        // eager subscribe eder), aksi halde geç abone yayımı kaçırır (SharedFlow sözleşmesi, bug değil).
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { received.add(it) }
        }
        vm.onIntent(CounterIntent.Increment)
        assertEquals(1, received.size)
        val effect = received.single()
        assertTrue(effect is CounterEffect.Toast)
        assertEquals("count=1", effect.text)
        job.cancel()
    }
}
