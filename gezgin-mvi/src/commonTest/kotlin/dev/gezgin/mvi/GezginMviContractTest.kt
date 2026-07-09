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
        // Kayıpsız GezginEffects (Channel) backing'iyle collector emit'ten önce de sonra da bağlanabilir;
        // burada önce bağlanıp gerçek-zamanlı teslim doğrulanır (UnconfinedTestDispatcher eager collect).
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

    /**
     * MJ2 kayıpsızlık kanıtı: gözlemci YOKKEN (Nav3'te örtülen / STOPPED entry'yi taklit eder) emit edilen
     * efektler, yeniden gözlenince (STARTED'a dönüş) SIRAYLA teslim edilir. `MutableSharedFlow(replay=0)`
     * backing'iyle bu iki efekt SESSİZCE DÜŞERDİ; [GezginEffects] (`Channel(UNLIMITED)`) ile kayıpsız.
     */
    @Test
    fun effects_emitted_while_unobserved_are_delivered_on_reobservation() = runTest {
        val vm = CounterViewModel(CounterRoute())
        // Henüz HİÇBİR collector yok — efektleri şimdi üret.
        vm.onIntent(CounterIntent.Increment)   // Toast(count=1)
        vm.onIntent(CounterIntent.Increment)   // Toast(count=2)

        // Şimdi "yeniden gözle": önce üretilen iki efekt kuyrukta tutulmuştu → sırayla gelir.
        val received = mutableListOf<CounterEffect>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { received.add(it) }
        }
        assertEquals(
            listOf("count=1", "count=2"),
            received.map { (it as CounterEffect.Toast).text },
        )
        job.cancel()
    }
}
