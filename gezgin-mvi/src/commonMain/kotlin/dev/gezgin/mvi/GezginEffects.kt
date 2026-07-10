package dev.gezgin.mvi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A lossless one-shot effect primitive (§10.1, Faz 5 recheck / MJ2) — the RECOMMENDED backing for
 * [GezginMvi.effects].
 *
 * **Why NOT `MutableSharedFlow` + `tryEmit`:** `MutableSharedFlow(replay = 0, extraBufferCapacity = n)` is a
 * HOT stream and, because `replay = 0`, it keeps a value emitted **while there is no subscriber** nowhere. In
 * Nav3 a covered entry leaves the composition ENTIRELY → no collector; or when the app goes to the background
 * (`STOPPED`), [ObserveEffects]'s `repeatOnLifecycle` collect is cut. If the VM calls `_effects.tryEmit(...)`
 * in this window: (a) if the buffer is empty the value is lost (no subscriber, no replay); (b) if the buffer
 * is full (`extraBufferCapacity` overflow — back-to-back effects in one frame) `tryEmit` returns `false` and
 * the effect is dropped anyway — and common fixtures ignore the return value. Result: when the user comes
 * back the snackbar/toast is NOT PLAYED. [ObserveEffects]'s "no toast lost when returning to STARTED" promise
 * holds ONLY with a buffered `Channel` backing.
 *
 * **Why `Channel(UNLIMITED)`:** a `Channel` is HOT but HOLDS the value until it is consumed. Even without a
 * subscriber, [send]-ed effects wait in the queue (`UNLIMITED` → [send] never suspends/blocks, never drops)
 * and are delivered IN ORDER when the single collector reconnects via [flow] ([receiveAsFlow]) (return to
 * STARTED / entry re-compose). An effect = exactly-once delivery; that is why [flow] is for a **single**
 * collector (`receiveAsFlow` does not fan out — a second observer shares the value, it does not duplicate it).
 *
 * **Stable instance:** [flow] is set up once (`val`) → the SAME `Flow` on every access. In the
 * `ObserveEffects(vm.effects) { ... }` call inside `@ScreenEffect`, the `LaunchedEffect(effects, ...)` key
 * does not change on recomposition, so the collector is not restarted needlessly. (A `get()` property that
 * calls `receiveAsFlow()` on every access is therefore WRONG — the key would change on every recomposition.)
 *
 * Usage:
 * ```
 * private val _effects = GezginEffects<CounterEffect>()
 * override val effects: Flow<CounterEffect> = _effects.flow
 * // ...
 * _effects.send(CounterEffect.Toast("saved"))
 * ```
 */
public class GezginEffects<E> {
    private val channel = Channel<E>(Channel.UNLIMITED)

    /**
     * The lossless effect stream — assigned directly to [GezginMvi.effects]. A stable (`val`) instance; for a
     * **single** collector (an effect = exactly-once delivery, no fan-out).
     */
    public val flow: Flow<E> = channel.receiveAsFlow()

    /**
     * Send an effect. The `UNLIMITED` buffer → never suspends, never drops: even without an observer it is
     * queued and delivered on re-observe. Callable from any thread.
     */
    public fun send(effect: E) {
        channel.trySend(effect)
    }
}
